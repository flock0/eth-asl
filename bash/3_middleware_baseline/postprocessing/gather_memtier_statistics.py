#!/usr/bin/python3

import subprocess
import re
import pandas as pd
import numpy as np
import math

### Extracts the client logs from a single memtier log. For each timestep we get xput and responsetime
def extract_client_logs(inputfile):
    print('Extracting stats from client log', inputfile)

    extracted_file = inputfile + ".extracted"

    with open(extracted_file, "w") as client_logfile:
        client_logfile.write("timestep throughput responsetime\n")

    bashCommand = "bash extract_single_memtier_xput_resptimes.sh {} {}".format(inputfile, extracted_file)
    process = subprocess.Popen(bashCommand.split(), stdout=subprocess.PIPE)
    _, error = process.communicate()

    if (error != None):
        print("Encountered error {} when extracting data".format(error))
        exit(2)

    return pd.read_csv(extracted_file, delim_whitespace=True)

### Extracts the client logs from multiple memtier logs and aggregates them.
### Throughput is aggregated by summing
### Responsetime is aggregated by a weighted average
def aggregate_over_clients(inputfiles):
    concatenated = pd.concat([extract_client_logs(inputfile) for inputfile in inputfiles])
    concatenated['product'] = concatenated['throughput'] * concatenated['responsetime']
    grouped = concatenated.groupby('timestep')
    aggregated = grouped.agg({'throughput': 'sum', 'product': 'sum'})
    aggregated['responsetime'] = aggregated['product'] / aggregated['throughput']
    aggregated.drop('product', axis=1, inplace=True)
    return aggregated.reset_index()

### Aggregates the timesteps from multiple reps
### For both throughput and responsetime we calculate the mean and variance
def aggregate_over_reps(rep_aggregates):
    concatenated = pd.concat(rep_aggregates)
    grouped = concatenated.groupby('timestep')
    aggregated = grouped.agg({'throughput': 'mean', 'responsetime': 'mean'})
    return aggregated

### Aggregates over all timesteps
### Takes a dataframe of throughput and variance, averaged by timesteps
### Returns a single average throughput and std value
def aggregate_over_timesteps(timestep_wise_averages):
    return timestep_wise_averages.agg(['mean', 'std'])
def extract_total_numbers(inputfile):

    for line in open(inputfile, 'r'):
        if line.startswith("Totals"):
            split_line = line.split()
            return float(split_line[1]), float(split_line[2]), float(split_line[3])

def extract_ping_logs(inputfile):
    print('Extracting stats from ping log', inputfile)

    extracted_file = inputfile + ".extracted"
    with open(extracted_file, "w") as client_ping_logfile:
        client_ping_logfile.write("clockTime count rtt\n")

    bashCommand = "bash extract_single_pings.sh {} {}".format(inputfile, extracted_file)
    process = subprocess.Popen(bashCommand.split(), stdout=subprocess.PIPE)
    _, error = process.communicate()

    if (error != None):
        print("Encountered error {} when extracting pings".format(error))
        exit(2)

    df = pd.read_csv(extracted_file, delim_whitespace=True)
    clockTime_regex = re.compile("(?<=\[)\d*(?=\.)")
    df['clockTime'] = df['clockTime'].apply(lambda time: int(clockTime_regex.findall(time)[0]))
    df['count'] = df['count'].apply(lambda count: count[9:])
    df['rtt'] = df['rtt'].apply(lambda rtt: float(rtt[5:]))

    df['clockTime'] = df['clockTime'] - df['clockTime'].min()
    return df

def aggregate_all_client_logs(metrics):
    return metrics.groupby(['timestep'], as_index=True).agg(
        {'throughput': 'sum', 'responsetime': 'mean'})

def calculate_throughput_resptime(metrics):
    xput = metrics['throughput'].mean()
    resptime = metrics['responsetime'].mean()
    return (xput, resptime)

def calculate_miss_rate(df):
    return df['misses_persec'].sum() / df['total_opsec'].sum()

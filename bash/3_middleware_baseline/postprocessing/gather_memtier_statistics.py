#!/usr/bin/python3

import subprocess
import re
import pandas as pd

def extract_client_logs(inputfile):
    print('Extracting stats from client log', inputfile)

    extracted_file = inputfile + ".extracted"

    with open(extracted_file, "w") as client_logfile:
        client_logfile.write("timestep throughput responsetime\n")

    bashCommand = "bash extract_single_memtier_xput_resptimes.sh {} {}".format(inputfile, extracted_file)
    process = subprocess.Popen(bashCommand.split(), stdout=subprocess.PIPE)
    _, error = process.communicate()

    return pd.read_csv(extracted_file, delim_whitespace=True)

def extract_ping_logs(inputfile, startingClockTime):
    print('Extracting stats from ping log', inputfile)

    extracted_file = inputfile + ".extracted"
    with open(extracted_file, "w") as client_ping_logfile:
        client_ping_logfile.write("clockTime count rtt\n")

    bashCommand = "bash extract_single_pings.sh {} {}".format(inputfile, extracted_file)
    process = subprocess.Popen(bashCommand.split(), stdout=subprocess.PIPE)
    _, error = process.communicate()

    df = pd.read_csv(extracted_file, delim_whitespace=True)
    clockTime_regex = re.compile("(?<=\[)\d*(?=\.)")
    df['clockTime'] = df['clockTime'].apply(lambda time: int(clockTime_regex.findall(time)[0]))
    df['count'] = df['count'].apply(lambda count: count[9:])
    df['rtt'] = df['rtt'].apply(lambda rtt: float(rtt[5:]))

    df['clockTime'] = df['clockTime'] - df['clockTime'].min()
    return df

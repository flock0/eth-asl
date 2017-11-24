import os
import re
import pandas as pd
import numpy as np

log_interval = 20

### Concatenates all CSV request logs into one big data framee
### The request CSV files are read from the inputdir
def concatenate_requestlogs(inputdir):

    print('Concatenating requestlogs in', inputdir)

    if (not os.path.isdir(inputdir)):
        print(inputdir, 'is doesn\'t exist')
        exit(2)

    threadid_regex = re.compile("(?<=requests_Thread-)\d*(?=.csv)")
    request_files_list = []
    for file in os.listdir(inputdir):
        if (file.startswith("requests")):
            csv_file = pd.read_csv(os.path.join(inputdir, file))
            csv_file['thread'] = threadid_regex.findall(file)[0]
            csv_file['middleware'] = inputdir
            request_files_list.append(csv_file)

    return pd.concat(request_files_list)

def sort_by_clock(requests):
    return requests.sort_values(by='initializeClockTime')

### Extracts the most important metrics from the raw request logs
def extract_metrics(requests):
    metrics = requests.loc[:, ['middleware', 'requestType', 'initializeClockTime', 'queueLength', 'requestSize', 'responseSize', 'thread', 'numHits', 'numKeysRequested']]
    metrics['queueTime_ms'] = (requests['dequeueTime'] - requests['enqueueTime']) / 1000000
    metrics['workerServiceTime_ms'] = (requests['completedTime'] - requests['dequeueTime']) / 1000000
    metrics['netthreadServiceTime_ms'] = (requests['enqueueTime'] - requests['arrivalTime']) / 1000000
    metrics['responseTime_us'] = (requests['completedTime'] - requests['arrivalTime']) / 1000
    metrics['responseTime_ms'] = metrics['responseTime_us'] / 1000
    metrics['timestep'] = (metrics['initializeClockTime'] - metrics['initializeClockTime'].min()) / 1000
    metrics = metrics.set_index('timestep')
    return metrics

### Aggregates the metrics over one-second windows and returns the means
### Assumes that the timestep is the index
def aggregate_over_windows(metrics):
    grouped = metrics.groupby(lambda x: int(x))
    means = grouped.agg('mean')
    means['throughput_mw'] = grouped['initializeClockTime'].count() * log_interval
    return means.rename_axis('timestep')

def aggregate_over_middlewares(metrics_list):
    concatenated = pd.concat(metrics_list).reset_index()
    grouped = concatenated.groupby(['timestep'], as_index=True)
    aggregated = grouped.mean()
    aggregated['throughput_mw'] = grouped.sum()['throughput_mw']
    return aggregated

### Aggregates the timesteps from multiple reps
### For the metrics we calculate the mean and variance
def aggregate_over_reps(rep_aggregates):
    concatenated = pd.concat(rep_aggregates)
    grouped = concatenated.groupby('timestep')
    aggregated = grouped.agg('mean')
    return aggregated

### Aggregates over all timesteps
### Takes a dataframe of metrics averaged by timesteps
### Returns a single average throughput and std value
def aggregate_over_timesteps(timestep_wise_averages):
    return timestep_wise_averages.agg(['mean', 'std'])

def calculate_aggregated_metrics(metrics):
    xput = metrics['initializeClockTime'].count() / (metrics['initializeClockTime'].max() - metrics['initializeClockTime'].min()) * log_interval
    resptime = metrics['responseTime_ms'].mean()
    queuetime = metrics['queueTime_ms'].mean()
    missrate = 1 - (metrics['numHits'].sum() / metrics['numKeysRequested'].sum())
    return (xput, resptime, queuetime, missrate)
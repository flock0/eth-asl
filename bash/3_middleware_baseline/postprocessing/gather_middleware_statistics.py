import os
import re
import pandas as pd

log_interval = 20

def concatenate_requestlogs(inputdir, prefix, mw_dir):

    print('Concatenating requestlogs in', inputdir)

    if (not os.path.isdir(inputdir)):
        print(inputdir, 'is doesn\'t exist')
        exit(2)

    threadid_regex = re.compile("(?<=" + prefix + "_Thread-)\d*(?=.csv)")
    request_files_list = []
    for file in os.listdir(inputdir):
        if (file.startswith(prefix)):
            csv_file = pd.read_csv(os.path.join(inputdir, file))
            csv_file['thread'] = threadid_regex.findall(file)[0]
            csv_file['middleware'] = mw_dir
            request_files_list.append(csv_file)

    return pd.concat(request_files_list)

def sort_by_clock(requests):
    return requests.sort_values(by='initializeClockTime')

def extract_metrics(requests):
    metrics = requests.loc[:, ['middleware', 'requestType', 'initializeClockTime', 'queueLength', 'requestSize', 'responseSize', 'thread', 'numHits', 'numKeysRequested']]
    metrics['queueTime_ms'] = (requests['dequeueTime'] - requests['enqueueTime']) / 1000000
    metrics['workerServiceTime_ms'] = (requests['completedTime'] - requests['dequeueTime']) / 1000000
    metrics['netthreadServiceTime_ms'] = (requests['enqueueTime'] - requests['arrivalTime']) / 1000000
    metrics['responseTime_us'] = (requests['completedTime'] - requests['arrivalTime']) / 1000
    metrics['responseTime_ms'] = metrics['responseTime_us'] / 1000
    metrics['initializeClockTime'] = (metrics['initializeClockTime'] - metrics['initializeClockTime'].min()) / 1000
    return metrics

def calculate_aggregated_metrics(metrics):
    xput = metrics['initializeClockTime'].count() / (metrics['initializeClockTime'].max() - metrics['initializeClockTime'].min()) * log_interval
    resptime = metrics['responseTime_ms'].mean()
    queuetime = metrics['queueTime_ms'].mean()
    missrate = 1 - (metrics['numHits'].sum() / metrics['numKeysRequested'].sum())
    return (xput, resptime, queuetime, missrate)
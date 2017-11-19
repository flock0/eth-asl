import os
import re
import pandas as pd

def concatenate_requestlogs(inputdir, prefix):

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
            request_files_list.append(csv_file)

    return pd.concat(request_files_list)

def sort_by_clock(requests):
    return requests.sort_values(by='initializeClockTime')

def extract_metrics(requests):
    metrics = requests.loc[:, ['requestType', 'initializeClockTime', 'queueLength', 'requestSize', 'responseSize', 'thread']]
    metrics['queueTime_ms'] = (requests['dequeueTime'] - requests['enqueueTime']) / 1000000
    metrics['workerServiceTime_ms'] = (requests['completedTime'] - requests['dequeueTime']) / 1000000
    metrics['netthreadServiceTime_ms'] = (requests['enqueueTime'] - requests['arrivalTime']) / 1000000
    metrics['responseTime_us'] = (requests['completedTime'] - requests['arrivalTime']) / 1000
    metrics['responseTime_ms'] = metrics['responseTime_us'] / 1000
    metrics['miss_rate'] = 1 - requests['numHits'] / requests['numKeysRequested']

    return metrics

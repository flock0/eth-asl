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

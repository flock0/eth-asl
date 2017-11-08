#!/usr/bin/python3

import sys, os, getopt, subprocess
import re

def append_to_csv(num_vc_per_thread, rep, source_path, destination_path):
    bash_command = "awk '{print " + num_vc_per_thread + ", " + rep + ", $0}' " + source_path + " >> " + destination_path
    process = subprocess.Popen(bashCommand.split(), stdout=subprocess.PIPE)
    _, error = process.communicate()

    if (error != None):
        print("Encountered error {} when extracting data".format(error))
        exit(2)

inputdir = ""
workload = ""
shouldOverwrite = False
clients = range(1,4)
output_csv_filename = "exp2_output.csv"
warmup_period_endtime = 10
cooldown_period_starttime = 80


try:
    opts, args = getopt.getopt(sys.argv[1:],"hri:w:",["idir=","workload="])
except getopt.GetoptError:
    print ('gather_statistics.py -i <input directory> -w <workload> -r')
    print ('<input directory> should contain all the collected log files.')
    print ('<workload> should be writeOnly or readOnly.')
    sys.exit(2)

for opt, arg in opts:
    if opt == '-h':
        print ('gather_statistics.py -i <input directory> -w <workload>')
        print ('<input directory> should contain all the collected log files.')
        print ('<workload> should be writeOnly or readOnly.')
        sys.exit()
    elif opt in ("-i", "--idir"):
        inputdir = arg
    elif opt in ("-w", "--workload"):
        workload = arg
    elif opt in ("-r", "--overwrite"):
        shouldOverwrite = True
print ('Input directory is "', inputdir)
print ('Workload is "', workload)


output_csv_filepath = os.path.join(inputdir, output_csv_filename)
# Should we overwrite the csv files?
if (shouldOverwrite and os.path.isfile(output_csv_filepath)):
    os.remove(output_csv_filepath)

# Gather VCs tested from directory names
workload_regex = re.compile(workload + "_\d*vc")
all_directories = filter(lambda x: os.path.isdir(os.path.join(inputdir, x)), os.listdir(inputdir))
matching_directories = [os.path.join(inputdir, dirname) for dirname in all_directories if workload_regex.match(dirname)]

# Gather num repetitions from sub directories
num_repetitions = -1
reps_regex = re.compile("\d*")
for directory in matching_directories:
    rep_list_len = len([1 for rep_dirname in os.listdir(directory) if reps_regex.match(rep_dirname)])
    if (num_repetitions == -1):
        num_repetitions = rep_list_len
    else:
        if (rep_list_len != num_repetitions):
            print ("Directory {} had {} reps, but expected {}".format(directory, rep_list_len, num_repetitions))
            exit(2)

# Extract individual metrics for one repetition
num_vc_regex = re.compile("(?<=" + workload + "_)\d*(?=vc)")
for experiment_dir in matching_directories:
    basedir = os.path.basename(experiment_dir)
    num_vc_per_thread = num_vc_regex.findall(basedir)[0]

    print ("Processing directory", experiment_dir)
    for rep in range(1, num_repetitions + 1):

        print ("    Repetition", rep)
        rep_directory = os.path.join(experiment_dir, str(rep))
        all_clients_throughputs = []
        all_clients_responsetimes = []
        print ("    Rep directory", rep_directory)
        bashCommand = "bash extract_xput_resptimes.sh {} {} {}".format(rep_directory,  warmup_period_endtime, cooldown_period_starttime)
        process = subprocess.Popen(bashCommand.split(), stdout=subprocess.PIPE)
        _, error = process.communicate()

        if (error != None):
            print("Encountered error {} when extracting data".format(error))
            exit(2)


        # Store extracted data in intermediate csv-file

        append_to_csv(num_vc_per_thread, str(rep), "clients.aggregated", output_csv_filepath)


# TODO     extract median latency (from histogram at the end)
# TODO Output numbers to final file
# TODO Calculate avg throughput
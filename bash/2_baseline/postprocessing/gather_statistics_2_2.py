#!/usr/bin/python3

import sys, os, getopt, subprocess
import re
import pandas as pd

def gather_statistics(inputdir, workload, shouldOverwrite = False):

    aggregated_csv_filename = "exp2_2_aggregated.csv"
    concatenated_csv_filename = "exp2_2_concatenated.csv"
    warmup_period_endtime = 10
    cooldown_period_starttime = 70


    print ('Input directory is "', inputdir)
    print ('Workload is "', workload)

    aggregated_csv_filename = workload + "_" + aggregated_csv_filename
    aggregated_csv_filepath = os.path.join(inputdir, aggregated_csv_filename)
    concatenated_csv_filename = workload + "_" + concatenated_csv_filename
    concatenated_csv_filepath = os.path.join(inputdir, concatenated_csv_filename)

    # Should we overwrite the csv files?
    if (shouldOverwrite and os.path.isfile(aggregated_csv_filepath)):
        os.remove(aggregated_csv_filepath)

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

    aggregate_dataframes_list = []
    concatenate_dataframes_list = []
    # Extract individual metrics for one repetition
    num_vc_regex = re.compile("(?<=" + workload + "_)\d*(?=vc)")
    for experiment_dir in matching_directories:
        basedir = os.path.basename(experiment_dir)
        num_vc_per_thread = num_vc_regex.findall(basedir)[0]

        print ("Processing directory", experiment_dir)
        for rep in range(1, num_repetitions + 1):

            print ("    Repetition", rep)
            rep_directory = os.path.join(experiment_dir, str(rep))
            print ("    Rep directory", rep_directory)

            # Extract relevant throughput and response time data from client logs (using bash scripting)
            with open(os.path.join(rep_directory, 'client_01_0.log.extracted'), "w") as client_logfile:
                client_logfile.write("client timestep throughput responsetime\n")
            with open(os.path.join(rep_directory, 'client_01_1.log.extracted'), "w") as client_logfile:
                client_logfile.write("client timestep throughput responsetime\n")

            bashCommand = "bash extract_xput_resptimes_2_2.sh {}".format(rep_directory)
            process = subprocess.Popen(bashCommand.split(), stdout=subprocess.PIPE)
            _, error = process.communicate()

            if (error != None):
                print("Encountered error {} when extracting data".format(error))
                exit(2)



            client1 = pd.read_csv(os.path.join(rep_directory, 'client_01_0.log.extracted'), delim_whitespace=True)
            client2 = pd.read_csv(os.path.join(rep_directory, 'client_01_1.log.extracted'), delim_whitespace=True)

            joined = client1.merge(client2, on='timestep', how='inner')
            joined['sum_throughput'] = joined['throughput_x'] + joined['throughput_y']
            joined['avg_responsetime'] = (joined['responsetime_x'] + joined['responsetime_y']) / 2

            joined['vc_per_thread'] = num_vc_per_thread
            joined['rep'] = rep
            joined = joined[joined['timestep'] > warmup_period_endtime]
            joined = joined[joined['timestep'] < cooldown_period_starttime]
            rep_aggregated_path = os.path.join(rep_directory, "clients.aggregated")

            single_aggregated_dataframe = joined.loc[:, ['vc_per_thread', 'rep', 'timestep', 'sum_throughput', 'avg_responsetime']]
            single_aggregated_dataframe.to_csv(rep_aggregated_path, index=False)
            aggregate_dataframes_list.append(single_aggregated_dataframe)

            concat = pd.concat([client1, client2])
            concat = concat[concat['timestep'] > warmup_period_endtime]
            concat = concat[concat['timestep'] < cooldown_period_starttime]
            concat['vc_per_thread'] = num_vc_per_thread
            concat['rep'] = rep

            rep_concatenated_path = os.path.join(rep_directory, "clients.concatenated")
            concat.to_csv(rep_concatenated_path, index=False)
            concatenate_dataframes_list.append(concat)


    pd.concat([frame for frame in aggregate_dataframes_list]).to_csv(aggregated_csv_filepath, index=False)
    pd.concat([frame for frame in concatenate_dataframes_list]).to_csv(concatenated_csv_filepath, index=False)

if __name__ == '__main__':
    inputdir = ""
    workload = ""
    shouldOverwrite = False
    try:
        opts, args = getopt.getopt(sys.argv[1:],"hri:w:",["inputdir=","workload="])
    except getopt.GetoptError:
        print ('gather_statistics_2_2.py -i <input directory> -w <workload> -r')
        print ('<input directory> should contain all the collected log files.')
        print ('<workload> should be writeOnly or readOnly.')
        sys.exit(2)

    for opt, arg in opts:
        if opt == '-h':
            print ('gather_statistics_2_2.py -i <input directory> -w <workload>')
            print ('<input directory> should contain all the collected log files.')
            print ('<workload> should be writeOnly or readOnly.')
            sys.exit()
        elif opt in ("-i", "--inputdir"):
            inputdir = arg
        elif opt in ("-w", "--workload"):
            workload = arg
        elif opt in ("-r", "--overwrite"):
            shouldOverwrite = True

    gather_statistics(inputdir, workload, shouldOverwrite)
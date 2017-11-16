import sys, os, getopt
import pandas as pd
from gather_statistics_2_2 import gather_statistics

def gather_averages(inputdir):

    concatenated_averages = []
    workloads=['writeOnly', 'readOnly']
    num_threads=2
    num_clients=1

    for workload in workloads:
        gather_statistics(inputdir, workload, True)

        csv_file = pd.read_csv(os.path.join(inputdir, workload + '_exp2_2_aggregated.csv'))
        csv_file['num_clients'] = csv_file['vc_per_thread'] * num_threads * num_clients
        grouped = csv_file.groupby(['rep', 'num_clients'])
        averages = grouped['sum_throughput', 'avg_responsetime'].mean()
        averages['workload'] = workload
        concatenated_averages.append(averages)

    concatenated_averages = pd.concat(concatenated_averages)
    return concatenated_averages


if __name__ == '__main__':
    inputdir = ""
    try:
        opts, args = getopt.getopt(sys.argv[1:],"i:",["inputdir="])
    except getopt.GetoptError:
        print ('gather_averages_2_2.py -i <input directory>')
        print ('<input directory> should contain all the collected log files.')
        sys.exit(2)

    for opt, arg in opts:
        if opt == '-h':
            print('gather_averages_2_2.py -i <input directory>')
            print('<input directory> should contain all the collected log files.')
            sys.exit()
        elif opt in ("-i", "--inputdir"):
            inputdir = arg

    gather_averages(inputdir)
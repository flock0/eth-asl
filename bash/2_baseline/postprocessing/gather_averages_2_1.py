import sys, os, getopt
import pandas as pd
from gather_statistics_2_1 import gather_statistics

def gather_averages(inputdir):

    workloads=["writeOnly", "readOnly"]
    for workload in workloads:
        gather_statistics(inputdir, workload, True)

        csv_file = pd.read_csv(os.path.join(inputdir, workload + '_exp2_1_aggregated.csv'))
        grouped = csv_file.groupby(['rep', 'vc_per_thread'])
        averages = grouped['sum_throughput', 'avg_responsetime'].mean()
        print(averages)


if __name__ == '__main__':
    inputdir = ""
    try:
        opts, args = getopt.getopt(sys.argv[1:],"i:",["inputdir="])
    except getopt.GetoptError:
        print ('2_1_gather_averages.py -i <input directory>')
        print ('<input directory> should contain all the collected log files.')
        sys.exit(2)

    for opt, arg in opts:
        if opt == '-h':
            print('2_1_gather_averages.py -i <input directory>')
            print('<input directory> should contain all the collected log files.')
            sys.exit()
        elif opt in ("-i", "--inputdir"):
            inputdir = arg

    gather_averages(inputdir)
import os
import gather_middleware_statistics as gmws
import gather_memtier_statistics as gmts
import matplotlib.pyplot as plt
from cycler import cycler
import directory_functions as dirfuncs
import pandas as pd
def plot_mw_metric_over_time(inputdir, worker_configuration, metric_to_plot, folder_prefix, reps, middlewares, ax):

    color_cycler = cycler('color', ['#66c2a4', '#41ae76', '#238b45', '#005824'])
    #ax.set_ylim([0, 27000])
    ax.set_xlim([-5, 87])
    ax.set_prop_cycle(color_cycler)

    for worker_config in worker_configuration:
        all_windows = []
        for rep in reps:
            log_folder_path = create_log_folder_path(inputdir, folder_prefix, rep, worker_config)

            middleware_dirs = [dirfuncs.get_only_subdir(os.path.join(log_folder_path, mw_dir)) for mw_dir in middlewares]
            requests = pd.concat([gmws.concatenate_requestlogs(middleware_dir) for middleware_dir in middleware_dirs])

            metrics = gmws.extract_metrics(requests)

            window = gmws.aggregate_over_windows(metrics)

            all_windows.append(window)

        concatenated = pd.concat(all_windows)[metric_to_plot]
        avg = concatenated.groupby(concatenated.index).agg('mean')
        ax.plot(avg.index, avg, label=worker_config)


    ax.legend(loc="upper left")
    ax.set_title("Experiment 3.1:\nThroughput (MW) vs. Time for different number of workers")
    ax.set_xlabel("Time (sec)")
    ax.set_ylabel("Throughput (ops/sec)")


        #fig.savefig(os.path.join(outdir, experiment_label, '{}_mw_throughput_{}.png'.format(workload, experiment_label)),dpi=300)


def plot_mt_metric_over_time(inputdir, worker_configuration, metric_to_plot, folder_prefix, reps, client_logfiles, ax):

    color_cycler = cycler('color', ['#66c2a4', '#41ae76', '#238b45', '#005824'])


    ax.set_prop_cycle(color_cycler)

    for worker_config in worker_configuration:
        all_windows = []
        for rep in reps:
            log_folder_path = create_log_folder_path(inputdir, folder_prefix, rep, worker_config)

            client_logfile_paths = [os.path.join(log_folder_path, client_logfile) for client_logfile in client_logfiles]
            window = gmts.aggregate_over_clients(client_logfile_paths)

            all_windows.append(window)

        concatenated = pd.concat(all_windows)[metric_to_plot]
        avg = concatenated.groupby(concatenated.index).agg('mean')
        ax.plot(avg.index, avg, label=worker_config)
        
    ax.set_xlim([-5, 87])
    ax.legend(loc="upper left")
    ax.set_title("Experiment 3.1:\nThroughput (MW) vs. Time for different number of workers")
    ax.set_xlabel("Time (sec)")
    ax.set_ylabel("Throughput (ops/sec)")


def create_log_folder_path(inputdir, folder_prefix, rep, worker_config):
    return os.path.join(inputdir, "{}{}workers".format(folder_prefix, worker_config), str(rep))

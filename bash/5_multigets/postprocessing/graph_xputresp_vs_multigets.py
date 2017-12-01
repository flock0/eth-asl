import os
from operator import concat

from directory_functions import *
import pandas as pd
from cut_away_warmup_cooldown import *
import gather_memtier_statistics as gmts
import gather_middleware_statistics as gmws
import matplotlib.pyplot as plt
from cycler import cycler
def create_workload_graphs(inputdir, experiment_label,  sharded, middlewares, client_logfiles, dir_suffix_regex_string, warmup_period_endtime, cooldown_period_starttime, outdir, num_threads, ylim_xput=27000, ylim_resp=30, xlim=350):
    # e.g. dir_suffix_regex_string = "_\d*vc\d*workers"

    print('Input directory is ', inputdir)
    print('Setting is ', sharded)
    print('Saving all graphs to ', outdir)


    if not os.path.exists(os.path.join(outdir, experiment_label)):
        os.makedirs(os.path.join(outdir, experiment_label))

    matching_directories = find_workload_dirs(inputdir, sharded + dir_suffix_regex_string)

    num_repetitions = get_and_validate_num_repetition(matching_directories)

    loop_function(experiment_label, matching_directories, num_repetitions, middlewares, client_logfiles, warmup_period_endtime, cooldown_period_starttime, sharded, outdir, num_threads, ylim_xput, ylim_resp, xlim)


def loop_function(experiment_label, all_directories, num_repetitions, middlewares, client_logfiles, warmup_period_endtime, cooldown_period_starttime, sharded, outdir, num_threads, ylim_xput, ylim_resp, xlim):

    averages = get_data(all_directories, num_repetitions, middlewares, client_logfiles, warmup_period_endtime, cooldown_period_starttime, num_threads)
    averages = averages.sort_values('multigetSize')

    # Calculate interactive law values
    avg = averages.reset_index()

    #avg = avg.set_index(['workers', 'num_clients', 'index'])

    plot_mw_xput_respTime_all_workers(avg, experiment_label, sharded, outdir, ylim_xput, ylim_resp, xlim)
    #plot_mw_queueLength_all_workers(avg, experiment_label, sharded, outdir, xlim)
    #plot_mw_memcachedRTT_all_workers(avg, experiment_label, sharded, outdir, xlim)

def get_data(matching_dirs, num_repetitions, middlewares, client_logfiles, warmup_period_endtime, cooldown_period_starttime, num_threads):
    all_metrics_per_multiget = []
    for experiment_dir in matching_dirs:
        num_multiget = find_num_multigets(experiment_dir)
        all_mw_metrics_per_rep = []
        all_mt_metrics_per_rep = []
        for rep in range(1, num_repetitions + 1):

            middleware_dirs = [get_only_subdir(os.path.join(experiment_dir, str(rep), mw_dir)) for mw_dir in middlewares]
            concatenated_requests = [gmws.concatenate_requestlogs(middleware_dir) for middleware_dir in middleware_dirs]


            filtered_requests = [reqs[reqs['requestType'].str.contains("GET")] for reqs in concatenated_requests]
            metrics = [gmws.extract_metrics(reqs) for reqs in filtered_requests]


            cut_metrics = [cut_away_warmup_cooldown(mets, warmup_period_endtime, cooldown_period_starttime) for mets in metrics]

            windows = [gmws.aggregate_over_windows(cut_mets) for cut_mets in cut_metrics]

            rep_metrics = gmws.aggregate_over_middlewares(windows)

            all_mw_metrics_per_rep.append(rep_metrics)


            client_logfile_paths = [os.path.join(experiment_dir, str(rep), client_logfile) for client_logfile in client_logfiles]
            client_metrics = gmts.aggregate_over_clients(client_logfile_paths)

            cut_client_metrics = cut_away_warmup_cooldown(client_metrics, warmup_period_endtime, cooldown_period_starttime)

            all_mt_metrics_per_rep.append(cut_client_metrics)


        # We have three throughput/resptimes values now from the three repetitions
        mw_agg_over_reps = gmws.aggregate_over_reps(all_mw_metrics_per_rep)
        mw_averages = gmws.aggregate_over_timesteps(mw_agg_over_reps)
        mt_agg_over_reps = gmts.aggregate_over_reps(all_mt_metrics_per_rep)
        mt_averages = gmts.aggregate_over_timesteps(mt_agg_over_reps)

        metrics_per_vc = pd.concat([mw_averages, mt_averages], axis=1)
        metrics_per_vc['multigetSize'] = num_multiget
        all_metrics_per_multiget.append(metrics_per_vc)

    all_metrics = pd.concat(all_metrics_per_multiget)

    return all_metrics

def plot_mw_xput_respTime_all_workers(avg, experiment_label, sharded, outdir, ylim_xput, ylim_resp, xlim):

    # Throughput using interactive laws
    fig, ax = plt.subplots()
    ax2 = ax.twinx()
    ax.set_ylim([0, ylim_xput])
    ax.set_xlim([0, xlim])
    ax2.set_ylim([0, ylim_resp])
    ax2.set_xlim([0, xlim])
    ax2.axvspan(74, 76, facecolor='#cc4c02', alpha=1)
    ax.set_prop_cycle(cycler('color', ['#238b45']))
    ax2.set_prop_cycle(cycler('color', ['#cc4c02']))
    mean_values = avg[avg['index'] == 'mean'].reset_index()
    std_values = avg[avg['index'] == 'std'].reset_index()
    ax.errorbar(mean_values['multigetSize'], mean_values['throughput'], yerr=std_values['throughput'], label="Throughput", marker='o', capsize=3)
    ax2.errorbar(mean_values['multigetSize'], mean_values['responsetime'], yerr=std_values['responsetime'], label="Response Time", marker='o', capsize=3)
    plt.xticks(mean_values['multigetSize'])
    ax.set_title("Throughput, Response Time (MT) vs. MultiGet sizes")
    ax.set_xlabel("Sizes of MultiGets")
    ax.set_ylabel("Throughput (op/s)", color='#238b45')
    ax2.set_ylabel("Response Time (ms)", color='#cc4c02')

    # plt.show()
    fig.savefig(os.path.join(outdir, experiment_label, '{}_mw_xput_resptime_{}.png'.format(sharded, experiment_label)), dpi=300)

def plot_mw_queueLength_all_workers(avg, experiment_label, workload, outdir, xlim):

    color_cycler = cycler('color', ['#9ebcda', '#8c6bb1', '#88419d', '#4d004b'])
    # Throughput using interactive laws
    fig, ax = plt.subplots()
    ax.set_ylim([0, 200])
    ax.set_xlim([0, xlim])
    ax.set_prop_cycle(color_cycler)
    for key, grp in avg.groupby(['workers']):
        mean_values = grp[grp['index'] == 'mean'].reset_index()
        std_values = grp[grp['index'] == 'std'].reset_index()
        ax.errorbar(mean_values['num_clients'], mean_values['queueLength'], yerr=std_values['queueLength'],
                    label=key, marker='o', capsize=3)
        plt.xticks(mean_values['num_clients'])
        ax.legend(loc="best", fontsize="small")
    ax.set_title("Queue Length vs. Number of clients\nfor differing worker count in the MW")
    ax.set_xlabel("Number of clients")
    ax.set_ylabel("Queue Length")

    # plt.show()
    fig.savefig(
        os.path.join(outdir, experiment_label, '{}_mw_queuelength_{}.png'.format(workload, experiment_label)),
        dpi=300)


def plot_mw_memcachedRTT_all_workers(avg, experiment_label, workload, outdir, xlim):

    color_cycler = cycler('color', ['#a6bddb', '#74a9cf', '#0570b0', '#023858'])
    # Throughput using interactive laws
    fig, ax = plt.subplots()
    #ax.set_ylim([0, 200])
    #ax.set_xlim([0, xlim])
    ax.set_prop_cycle(color_cycler)
    for key, grp in avg.groupby(['workers']):
        mean_values = grp[grp['index'] == 'mean'].reset_index()
        std_values = grp[grp['index'] == 'std'].reset_index()
        ax.errorbar(mean_values['num_clients'], mean_values['memcachedRTT_ms'], yerr=std_values['memcachedRTT_ms'],
                    label=key, marker='o', capsize=3)
        plt.xticks(mean_values['num_clients'])
        ax.legend(loc="best", fontsize="small")
    ax.set_title("Memcached Round-Trip Time vs. Number of clients\nfor differing worker count in the MW")
    ax.set_xlabel("Number of clients")
    ax.set_ylabel("Memcached RTT (ms)")

    # plt.show()
    fig.savefig(
        os.path.join(outdir, experiment_label, '{}_mw_memcachedRTT_{}.png'.format(workload, experiment_label)),
        dpi=300)


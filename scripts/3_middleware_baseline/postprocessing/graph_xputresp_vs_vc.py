import os
from operator import concat

import pandas as pd
from directory_functions import *
from cut_away_warmup_cooldown import *
import gather_memtier_statistics as gmts
import gather_middleware_statistics as gmws
import matplotlib.pyplot as plt
from cycler import cycler
def create_workload_graphs(inputdir, experiment_label,  workload, middlewares, client_logfiles, dir_suffix_regex_string, warmup_period_endtime, cooldown_period_starttime, outdir, num_threads, ylim_xput=27000, ylim_resp=30, xlim=350):
    # e.g. dir_suffix_regex_string = "_\d*vc\d*workers"

    print('Input directory is "', inputdir)
    print('Workload is "', workload)
    print('Saving all graphs to "', outdir)


    if not os.path.exists(os.path.join(outdir, experiment_label)):
        os.makedirs(os.path.join(outdir, experiment_label))

    matching_directories = find_workload_dirs(inputdir, workload + dir_suffix_regex_string)

    num_repetitions = get_and_validate_num_repetition(matching_directories)

    loop_function(experiment_label, matching_directories, num_repetitions, middlewares, client_logfiles, warmup_period_endtime, cooldown_period_starttime, workload, outdir, num_threads, ylim_xput, ylim_resp, xlim)


def loop_function(experiment_label, all_directories, num_repetitions, middlewares, client_logfiles, warmup_period_endtime, cooldown_period_starttime, workload, outdir, num_threads, ylim_xput, ylim_resp, xlim):

    worker_configurations = find_worker_configurations(all_directories)

    # Plot throughput and responsetime measured at middlewares
    all_worker_averages = []
    for worker_config in worker_configurations:
        matching_dirs = find_matching_worker_config_dirs(all_directories, worker_config)
        single_worker_averages = get_worker_data(worker_config, matching_dirs, num_repetitions, middlewares, client_logfiles, warmup_period_endtime, cooldown_period_starttime, num_threads)
        single_worker_averages['workers'] = worker_config
        all_worker_averages.append(single_worker_averages)

    all_worker_averages = pd.concat(all_worker_averages)
    sorted_worker_averages = all_worker_averages.sort_values('num_clients')

    # Calculate interactive law values
    avg = sorted_worker_averages.reset_index()
    avg['interact_resptime'] = avg['num_clients'] / avg['throughput'] * 1000
    avg['interact_throughput'] = avg['num_clients'] / avg['responsetime'] * 1000
    #avg = avg.set_index(['workers', 'num_clients', 'index'])

    plot_mw_xput_respTime_all_workers(avg, experiment_label, workload, outdir, ylim_xput, ylim_resp, xlim)
    plot_mt_xput_respTime_all_workers_wInteract(avg, experiment_label, workload, outdir, ylim_xput, ylim_resp, xlim)
    plot_mw_queueLength_all_workers(avg, experiment_label, workload, outdir, xlim)

def get_worker_data(worker_config, matching_dirs, num_repetitions, middlewares, client_logfiles, warmup_period_endtime, cooldown_period_starttime, num_threads):
    all_metrics_per_vc = []
    for experiment_dir in matching_dirs:
        num_vc = find_num_vc(experiment_dir)
        all_mw_metrics_per_rep = []
        all_mt_metrics_per_rep = []
        for rep in range(1, num_repetitions + 1):

            middleware_dirs = [get_only_subdir(os.path.join(experiment_dir, str(rep), mw_dir)) for mw_dir in middlewares]
            concatenated_requests = [gmws.concatenate_requestlogs(middleware_dir) for middleware_dir in middleware_dirs]

            metrics = [gmws.extract_metrics(reqs) for reqs in concatenated_requests]


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
        metrics_per_vc['vc_per_thread'] = num_vc
        all_metrics_per_vc.append(metrics_per_vc)

    all_metrics_for_worker = pd.concat(all_metrics_per_vc)
    all_metrics_for_worker['num_clients'] = all_metrics_for_worker['vc_per_thread'] * num_threads
    return all_metrics_for_worker

def plot_mw_xput_respTime_all_workers(avg, experiment_label, workload, outdir, ylim_xput, ylim_resp, xlim):

    color_cycler = cycler('color', ['#ccece6', '#66c2a4', '#238b45', '#00441b'])
    # Throughput using interactive laws
    fig, ax = plt.subplots()
    ax.set_ylim([0, ylim_xput])
    ax.set_xlim([0, xlim])
    ax.set_prop_cycle(color_cycler)
    for key, grp in avg.groupby(['workers']):
        mean_values = grp[grp['index'] == 'mean'].reset_index()
        std_values = grp[grp['index'] == 'std'].reset_index()
        ax.errorbar(mean_values['num_clients'], mean_values['throughput_mw'], yerr=std_values['throughput_mw'],
                    label=key, marker='o', capsize=3)
        plt.xticks(mean_values['num_clients'])
        ax.legend(loc="best", fontsize="small")
    ax.set_title("Throughput (MW) vs. Number of clients\nfor differing worker count in the MW")
    ax.set_xlabel("Number of clients")
    ax.set_ylabel("Throughput (ops/sec)")

    #plt.show()
    fig.savefig(os.path.join(outdir, experiment_label, '{}_mw_throughput_{}.png'.format(workload, experiment_label)), dpi=300)

    color_cycler = cycler('color', ['#fee391', '#fe9929', '#cc4c02', '#662506'])
    fig, ax = plt.subplots()
    ax.set_ylim([0, ylim_resp])
    ax.set_xlim([0, xlim])
    ax.set_prop_cycle(color_cycler)
    for key, grp in avg.groupby(['workers']):
        mean_values = grp[grp['index'] == 'mean'].reset_index()
        std_values = grp[grp['index'] == 'std'].reset_index()
        ax.errorbar(mean_values['num_clients'], mean_values['responseTime_ms'], yerr=std_values['responseTime_ms'],
                    label=key, marker='o', capsize=3)
        plt.xticks(mean_values['num_clients'])
    ax.legend(loc="best", fontsize="small")
    ax.set_title("Response Time (MW) vs. Number of clients\nfor differing worker count in the MW")
    ax.set_xlabel("Number of clients")
    ax.set_ylabel("Response Time (msec)")

    # plt.show()
    fig.savefig(os.path.join(outdir, experiment_label, '{}_mw_responsetime_{}.png'.format(workload, experiment_label)), dpi=300)

def plot_mt_xput_respTime_all_workers_wInteract(avg, experiment_label, workload, outdir, ylim_xput, ylim_resp, xlim):

    color_cycler = cycler('color', ['#ccece6', '#ccece6', '#66c2a4', '#66c2a4', '#238b45', '#238b45', '#00441b', '#00441b'])
    # Throughput using interactive laws
    fig, ax = plt.subplots()
    ax.set_ylim([0, ylim_xput])
    ax.set_xlim([0, xlim])
    ax.set_prop_cycle(color_cycler)
    for key, grp in avg.groupby(['workers']):
        mean_values = grp[grp['index'] == 'mean'].reset_index()
        std_values = grp[grp['index'] == 'std'].reset_index()
        ax.errorbar(mean_values['num_clients'], mean_values['throughput'],
                    yerr=std_values['throughput'],
                    label=key, marker='o', capsize=3)
        ax.plot(mean_values['num_clients'], mean_values['interact_throughput'], label="{} (i)".format(key), marker='o',
                linestyle='--')
        plt.xticks(mean_values['num_clients'])
        ax.legend(loc="best", fontsize="small")
    ax.set_title("Throughput (MT) vs. Number of clients\nfor differing worker count in the MW")
    ax.set_xlabel("Number of clients")
    ax.set_ylabel("Throughput (ops/sec)")

    # plt.show()
    fig.savefig(os.path.join(outdir, experiment_label, '{}_mt_throughput_{}.png'.format(workload, experiment_label)), dpi=300)

    color_cycler = cycler('color', ['#fee391', '#fee391', '#fe9929', '#fe9929', '#cc4c02', '#cc4c02', '#662506', '#662506'])
    fig, ax = plt.subplots()
    ax.set_ylim([0, ylim_resp])
    ax.set_xlim([0, xlim])
    ax.set_prop_cycle(color_cycler)
    for key, grp in avg.groupby(['workers']):
        mean_values = grp[grp['index'] == 'mean'].reset_index()
        std_values = grp[grp['index'] == 'std'].reset_index()
        ax.errorbar(mean_values['num_clients'],  mean_values['responsetime'], yerr=std_values['responsetime'],
                    label=key, marker='o', capsize=3)
        ax.plot(mean_values['num_clients'], mean_values['interact_resptime'], label="{} (i)".format(key), marker='o',
                linestyle='--')
        plt.xticks(mean_values['num_clients'])
    ax.legend(loc="best", fontsize="small")
    ax.set_title("Response Time (MT) vs. Number of clients\nfor differing worker count in the MW")
    ax.set_xlabel("Number of clients")
    ax.set_ylabel("Response Time (msec)")

    # plt.show()
    fig.savefig(os.path.join(outdir, experiment_label, '{}_mt_responsetime_{}.png'.format(workload, experiment_label)), dpi=300)

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
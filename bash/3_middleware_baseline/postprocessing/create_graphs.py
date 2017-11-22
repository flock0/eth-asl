import os
import pandas as pd
from directory_functions import *
from process_requestlogs import *
from cut_away_warmup_cooldown import *
import gather_memtier_statistics as gmt
import matplotlib.pyplot as plt
from cycler import cycler
import max_xput_keeper
def create_workload_graphs(inputdir, experiment_label,  workload, middlewares, client_logfiles, dir_suffix_regex_string, warmup_period_endtime, cooldown_period_starttime, outdir):
    # e.g. dir_suffix_regex_string = "_\d*vc\d*workers"

    print('Input directory is "', inputdir)
    print('Workload is "', workload)
    print('Saving all graphs to "', outdir)

    mxk = max_xput_keeper.MaxXputKeeper()

    if not os.path.exists(outdir):
        os.makedirs(outdir)

    matching_directories = find_workload_dirs(inputdir, workload + dir_suffix_regex_string)

    num_repetitions = get_and_validate_num_repetition(matching_directories)

    loop_function(experiment_label, matching_directories, num_repetitions, middlewares, client_logfiles, warmup_period_endtime, cooldown_period_starttime, workload, mxk, outdir)

    mxk.print()

def loop_function(experiment_label, all_directories, num_repetitions, middlewares, client_logfiles, warmup_period_endtime, cooldown_period_starttime, workload, mxk, outdir):

    worker_configurations = find_worker_configurations(all_directories)

    # Plot throughput and responsetime measured at middlewares
    all_worker_averages = []
    for worker_config in worker_configurations:
        matching_dirs = find_matching_worker_config_dirs(all_directories, worker_config)
        single_worker_averages = get_worker_data(worker_config, matching_dirs, num_repetitions, middlewares, client_logfiles, warmup_period_endtime, cooldown_period_starttime, mxk)
        single_worker_averages['workers'] = worker_config
        all_worker_averages.append(single_worker_averages)

    all_worker_averages = pd.concat(all_worker_averages)
    sorted_worker_averages = all_worker_averages.sort_values(by='workers')

    plot_mw_xput_respTime_all_workers(all_worker_averages, experiment_label, workload, outdir)
    plot_mt_xput_respTime_all_workers_wInteract(all_worker_averages, experiment_label, workload, outdir)

def get_worker_data(worker_config, matching_dirs, num_repetitions, middlewares, client_logfiles, warmup_period_endtime, cooldown_period_starttime, mxk):
    all_metrics_per_vc = []
    for experiment_dir in matching_dirs:
        num_vc = find_num_vc(experiment_dir)
        all_mw_metrics_per_rep = []
        all_mt_metrics_per_rep = []
        for rep in range(1, num_repetitions + 1):
            all_reqs_from_all_mws_list = []
            for mw_dir in middlewares:
                # Go down to the middleware directory and down the only directory
                middleware_dir = get_only_subdir(os.path.join(experiment_dir, str(rep), mw_dir))

                requests = concatenate_requestlogs(middleware_dir, "requests", mw_dir)

                all_reqs_from_all_mws_list.append(requests)
            requests = pd.concat(all_reqs_from_all_mws_list)
            sort_by_clock(requests)

            metrics = extract_metrics(requests)
            metrics.reset_index()

            cut_metrics = cut_away_warmup_cooldown(metrics, 'initializeClockTime', warmup_period_endtime, cooldown_period_starttime)

            # Calculate throughput/resptime number and add it to all_metrics_per_rep
            mw_aggregated_metrics = calculate_aggregated_metrics(cut_metrics)
            mxk.tryset_mw_max(mw_aggregated_metrics[0], mw_aggregated_metrics[1], mw_aggregated_metrics[2], mw_aggregated_metrics[3], num_vc, worker_config, rep)
            all_mw_metrics_per_rep.append(mw_aggregated_metrics)

            all_logs_from_all_mts_list = []
            totals_list = []
            for client_logfile in client_logfiles:
                client_logfile_path = os.path.join(experiment_dir, str(rep), client_logfile)
                client_logs = gmt.extract_client_logs(client_logfile_path)
                total_hits_misses = gmt.extract_total_numbers(client_logfile_path)
                totals_list.append(total_hits_misses)
                all_logs_from_all_mts_list.append(client_logs)

            totals_df = pd.DataFrame(data=totals_list, columns=['total_opsec', 'hits_persec', 'misses_persec'])
            missrate = gmt.calculate_miss_rate(totals_df)
            client_logs = pd.concat(all_logs_from_all_mts_list)
            cut_client_metrics = cut_away_warmup_cooldown(client_logs, 'timestep', warmup_period_endtime, cooldown_period_starttime)
            mt_aggregate_logs = gmt.aggregate_all_client_logs(cut_client_metrics)

            mt_throughput_resp = gmt.calculate_throughput_resptime(mt_aggregate_logs)
            mxk.tryset_mt_max(mt_throughput_resp[0], mt_throughput_resp[1], missrate, num_vc, worker_config, rep)
            all_mt_metrics_per_rep.append(mt_throughput_resp)


        # We have three throughput/resptimes values now from the three repetitions

        mw_metrics_per_vc= pd.DataFrame(data=all_mw_metrics_per_rep, columns=['throughput_mw_opsec', 'responseTime_mw_ms', 'queueTime_ms', 'missRate_mw'])
        mt_metrics_per_vc = pd.DataFrame(data=all_mt_metrics_per_rep, columns=['throughput_mt_opsec', 'responseTime_mt_ms'])
        metrics_per_vc = pd.concat([mw_metrics_per_vc, mt_metrics_per_vc], axis=1)
        metrics_per_vc['vc_per_thread'] = num_vc
        all_metrics_per_vc.append(metrics_per_vc)

    all_metrics_for_worker = pd.concat(all_metrics_per_vc)
    all_metrics_for_worker['num_clients'] = all_metrics_for_worker['vc_per_thread'] * 2
    avg = all_metrics_for_worker.groupby(['num_clients'], as_index=True).agg(
        {'throughput_mw_opsec': ['mean', 'std'], 'responseTime_mw_ms': ['mean', 'std'], 'throughput_mt_opsec': ['mean', 'std'], 'responseTime_mt_ms': ['mean', 'std']})
    avg = avg.reset_index()
    # Calculate throughput and responsetime using interactive responsetime laws
    avg['interact_responsetime_mean'] = avg['num_clients'] / avg[('throughput_mt_opsec', 'mean')] * 1000
    avg['interact_throughput_mean'] = avg['num_clients'] / avg[('responseTime_mt_ms', 'mean')] * 1000

    return avg

def plot_mw_xput_respTime_all_workers(avg, experiment_label, workload, outdir):

    color_cycler = cycler('color', ['#66c2a4', '#41ae76', '#238b45', '#005824'])
    # Throughput using interactive laws
    fig, ax = plt.subplots()
    #ax.set_ylim([0, 50000])
    ax.set_xlim([0, 70])
    ax.set_prop_cycle(color_cycler)
    for key, grp in avg.groupby(['workers']):
        ax.errorbar(grp['num_clients'], grp[('throughput_mw_opsec', 'mean')], yerr=2 * grp[('throughput_mw_opsec', 'std')],
                    label=key, marker='o', capsize=3)
        plt.xticks(grp['num_clients'])
        ax.legend(loc="upper left")
    ax.set_title("Experiment 3.1:\nThroughput (MW) vs. Number of clients\nfor different number of workers in the MW")
    ax.set_xlabel("Number of clients")
    ax.set_ylabel("Throughput (ops/sec)")

    #plt.show()
    fig.savefig(os.path.join(outdir, '{}_mw_throughput_{}.png'.format(workload, experiment_label)), dpi=300)




    fig, ax = plt.subplots()
    # ax.set_ylim([0, 50000])
    ax.set_xlim([0, 70])
    ax.set_prop_cycle(color_cycler)
    for key, grp in avg.groupby(['workers']):
        ax.errorbar(grp['num_clients'], grp[('responseTime_mw_ms', 'mean')], yerr=2 * grp[('responseTime_mw_ms', 'std')],
                    label=key, marker='o', capsize=3)
        plt.xticks(grp['num_clients'])
    ax.legend(loc="upper left")
    ax.set_title("Experiment 3.1:\nResponse Time (MW) vs. Number of clients\nfor different number of workers in the MW")
    ax.set_xlabel("Number of clients")
    ax.set_ylabel("Response Time (msec)")

    # plt.show()
    fig.savefig(os.path.join(outdir, '{}_mw_responsetime_{}.png'.format(workload, experiment_label)), dpi=300)

def plot_mt_xput_respTime_all_workers_wInteract(avg, experiment_label, workload, outdir):

    color_cycler = cycler('color', ['#66c2a4', '#66c2a4', '#41ae76', '#41ae76', '#238b45', '#238b45', '#005824', '#005824'])
    # Throughput using interactive laws
    fig, ax = plt.subplots()
    # ax.set_ylim([0, 50000])
    ax.set_xlim([0, 70])
    ax.set_prop_cycle(color_cycler)
    for key, grp in avg.groupby(['workers']):
        ax.errorbar(grp['num_clients'], grp[('throughput_mt_opsec', 'mean')],
                    yerr=2 * grp[('throughput_mt_opsec', 'std')],
                    label=key, marker='o', capsize=3)
        ax.plot(grp['num_clients'], grp['interact_throughput_mean'], label="{} (interact)".format(key), marker='o',
                linestyle='--')
        plt.xticks(grp['num_clients'])
        ax.legend(loc="upper left")
    ax.set_title(
        "Experiment 3.1:\nThroughput (MT) vs. Number of clients\nfor different number of workers in the MW")
    ax.set_xlabel("Number of clients")
    ax.set_ylabel("Throughput (ops/sec)")

    # plt.show()
    fig.savefig(os.path.join(outdir, '{}_mt_throughput_{}.png'.format(workload, experiment_label)), dpi=300)

    fig, ax = plt.subplots()
    # ax.set_ylim([0, 50000])
    ax.set_xlim([0, 70])
    ax.set_prop_cycle(color_cycler)
    for key, grp in avg.groupby(['workers']):
        ax.errorbar(grp['num_clients'], grp[('responseTime_mt_ms', 'mean')],
                    yerr=2 * grp[('responseTime_mt_ms', 'std')],
                    label=key, marker='o', capsize=3)
        ax.plot(grp['num_clients'], grp['interact_responsetime_mean'], label="{} (interact)".format(key), marker='o',
                linestyle='--')
        plt.xticks(grp['num_clients'])
    ax.legend(loc="upper left")
    ax.set_title(
        "Experiment 3.1:\nResponse Time (MT) vs. Number of clients\nfor different number of workers in the MW")
    ax.set_xlabel("Number of clients")
    ax.set_ylabel("Response Time (msec)")

    # plt.show()
    fig.savefig(os.path.join(outdir, '{}_mt_responsetime_{}.png'.format(workload, experiment_label)), dpi=300)
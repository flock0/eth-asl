import os
import gather_memtier_statistics as gmts
import gather_middleware_statistics as gmws
import directory_functions as dirfuncs
import cut_away_warmup_cooldown as cut
import pandas as pd

def find_max_throughput(inputdir, workload, worker, vc_configs, reps, client_logfiles, warmup_period_endtime, cooldown_period_starttime):
    unformatted_foldername= "{}_{}vc{}workers"

    config_throughput_maxima = []
    for vc in vc_configs:

        foldername = unformatted_foldername.format(workload, vc, worker)
        all_rep_windows = []
        for rep in range(1, reps+1):

            log_folder_path = os.path.join(inputdir, foldername, str(rep))

            client_logfile_paths = [os.path.join(log_folder_path, client_logfile) for client_logfile in client_logfiles]
            window = gmts.aggregate_over_clients(client_logfile_paths)
            window = cut.cut_away_warmup_cooldown(window, warmup_period_endtime, cooldown_period_starttime)
            all_rep_windows.append(window)

        mt_agg_over_reps = gmts.aggregate_over_reps(all_rep_windows)
        mt_averages = gmts.aggregate_over_timesteps(mt_agg_over_reps)
        config_throughput_maxima.append((vc, mt_averages.loc['mean', 'throughput']))

    all_config_maxima = pd.DataFrame(data=config_throughput_maxima, columns=['vc', 'throughput'])
    max_configuration = all_config_maxima.iloc[all_config_maxima['throughput'].idxmax()]
    return max_configuration

def average_pings(inputdir, workload, worker, vc, rep, ping_logfiles):
    unformatted_foldername = "{}_{}vc{}workers"
    foldername = unformatted_foldername.format(workload, str(int(vc)), str(int(worker)))
    log_folder_path = os.path.join(inputdir, foldername, str(int(rep)))
    ping_log_filepaths = [os.path.join(log_folder_path, ping_logfile) for ping_logfile in ping_logfiles]
    ping_logs = [gmts.extract_ping_logs(filepath) for filepath in ping_log_filepaths ]
    return pd.concat(ping_logs)['rtt'].mean()
def average_pings_over_reps(inputdir, workload, worker, vc, reps, ping_logfiles):
    lst = [average_pings(inputdir, workload, worker, vc, rep, ping_logfiles) for rep in range(1, reps+1)]
    return sum(lst)/len(lst)
def extract_summary_from_config(inputdir, workload, worker, vc, reps, xput_client, thinktime_ms, num_threads, middlewares, warmup_period_endtime, cooldown_period_starttime):
    unformatted_foldername = "{}_{}vc{}workers"
    foldername = unformatted_foldername.format(workload, str(int(vc)), str(int(worker)))

    all_reps = []
    for rep in range(1, reps+1):

        log_folder_path = os.path.join(inputdir, foldername, str(int(rep)))

        # Now we extract throughput, responsetime, average queuetime and missrate from the middleware
        middleware_dirs = [dirfuncs.get_only_subdir(os.path.join(log_folder_path, mw_dir)) for mw_dir in middlewares]
        concatenated_requests = [gmws.concatenate_requestlogs(middleware_dir) for middleware_dir in middleware_dirs]
        metrics = [gmws.extract_metrics(reqs) for reqs in concatenated_requests]
        cut_metrics = [cut.cut_away_warmup_cooldown(mets, warmup_period_endtime, cooldown_period_starttime) for mets in metrics]
        windows = [gmws.aggregate_over_windows(cut_mets) for cut_mets in cut_metrics]
        rep_metrics = gmws.aggregate_over_middlewares(windows)
        all_reps.append(rep_metrics)

    mw_agg_over_reps = gmws.aggregate_over_reps(all_reps)
    mw_averages = gmws.aggregate_over_timesteps(mw_agg_over_reps)
    avg = mw_averages.loc['mean', :]
    num_clients = vc * num_threads
    print("{} workers:\n".format(worker))
    print("Throughput MW:\t\t\t\t\t\t\t\t{}\nThroughput (derived from MW ResponseTime):\t{}\nThroughput (Client):\t\t\t\t\t\t{}\nAvg Time in Queue:\t\t\t\t\t\t\t{}\nAvg Length of Queue:\t\t\t\t\t\t{}\nAvg Time waiting for MC:\t\t\t\t\t{}\n".format(
        avg['throughput_mw'],
        num_clients / (float(avg['responseTime_ms']) + thinktime_ms) * 1000,
        xput_client,
        avg['queueTime_ms'],
        avg['queueLength'],
        avg['memcachedRTT_ms']
    ))
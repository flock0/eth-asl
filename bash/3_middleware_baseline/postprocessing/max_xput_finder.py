import os
import gather_memtier_statistics as gmts
import gather_middleware_statistics as gmws
import directory_functions as dirfunc
import max_xput_keeper
import cut_away_warmup_cooldown as cut
import pandas as pd

def find_max_throughput(inputdir, workload, worker_configs, vc_configs, reps, middlewares, client_logfiles, warmup_period_endtime, cooldown_period_starttime):
    unformatted_foldername= "{}_{}vc{}workers"
    mxk = max_xput_keeper.MaxXputKeeper()

    config_throughput_maximums = []
    for vc in vc_configs:
        for worker in worker_configs:

            foldername = unformatted_foldername.format(workload, vc, worker)
            all_rep_windows = []
            for rep in reps:

                log_folder_path = os.path.join(inputdir, foldername, str(rep))

                client_logfile_paths = [os.path.join(log_folder_path, client_logfile) for client_logfile in client_logfiles]
                window = gmts.aggregate_over_clients(client_logfile_paths)
                window = cut.cut_away_warmup_cooldown(window, warmup_period_endtime, cooldown_period_starttime)
                window['rep'] = rep
                all_rep_windows.append(window)

            reps_concatenated = pd.concat(all_rep_windows).reset_index(drop=True).set_index(['rep', 'timestep'])
            idxmax = reps_concatenated['throughput'].idxmax()
            config_throughput_maximums.append((vc, worker, idxmax[0], idxmax[1], reps_concatenated['throughput'].max()))

    all_config_maxima = pd.DataFrame(data=config_throughput_maximums, columns=['vc', 'worker', 'rep', 'timestep', 'throughput'])
    print("Maximum {} throughput achieved at:".format(workload))
    max_configuration = all_config_maxima.iloc[all_config_maxima['throughput'].idxmax()]
    print(max_configuration)

    # Now that we got the maximum configuration, we can extract the response time
    # at that timestep and (unfortunately only the total missrate)
    foldername = unformatted_foldername.format(workload, max_configuration['vc'], max_configuration['worker'])
    log_folder_path = os.path.join(inputdir, foldername, str(max_configuration['rep']))
    client_logfile_paths = [os.path.join(log_folder_path, client_logfile) for client_logfile in client_logfiles]
    window = gmts.aggregate_over_clients(client_logfile_paths)
    responsetime = window[window['timestep'] == max_configuration['timestep']]['responsetime']

    total_hits_misses = [gmts.extract_total_numbers(filepath) for filepath in client_logfile_paths]
    totals_df = pd.DataFrame(data=total_hits_misses, columns=['total_opsec', 'hits_persec', 'misses_persec'])
    missrate = gmts.calculate_miss_rate(totals_df)
    print ("responsetime\t{}".format(responsetime))
    print("missrate\t{}".format(missrate))

    # Now we extract throughput, responsetime, average queuetime and missrate from the middleware
    middleware_dirs = [dirfunc.get_only_subdir(os.path.join(log_folder_path, mw_dir)) for mw_dir in middlewares]
    concatenated_requests = [gmws.concatenate_requestlogs(middleware_dir) for middleware_dir in middleware_dirs]
    metrics = [gmws.extract_metrics(reqs) for reqs in concatenated_requests]
    cut_metrics = [cut.cut_away_warmup_cooldown(mets, warmup_period_endtime, cooldown_period_starttime) for mets in metrics]
    windows = [gmws.aggregate_over_windows(cut_mets) for cut_mets in cut_metrics]
    rep_metrics = gmws.aggregate_over_middlewares(windows)
    single_window = rep_metrics.loc[max_configuration['timestep']]

    print(single_window.apply(lambda x: format(x, 'f')))

inputdir = "/home/flo/Downloads/exp3/third_exec/3_1_middleware_baseline_onemw_2017-11-23_182327"
workload = "writeOnly"
worker_configs = [8, 16, 32, 64]
vc_configs = [1, 4, 8, 16, 24, 32, 48, 64]
reps = [1, 2, 3]
middlewares = ["middleware_04"]
client_logfiles = ["client_01.log"]
warmup_period_endtime = 10
cooldown_period_starttime = 72
find_max_throughput(inputdir, workload, worker_configs, vc_configs, reps, middlewares, client_logfiles, warmup_period_endtime, cooldown_period_starttime)
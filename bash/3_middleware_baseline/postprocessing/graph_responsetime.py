import os
import pandas as pd
import gather_middleware_statistics as gmws
import gather_memtier_statistics as gmts
import cut_away_warmup_cooldown as cut
import directory_functions as dirfuncs
import matplotlib.pyplot as plt


def graph_responsetime_withPing(worker, vc, num_threads, workload, middlewares, ping_logfile, client_logfiles, reps, inputdir, xlim, ax):

    all_mw_metrics_per_rep = []
    all_mt_metrics_per_rep = []
    for rep in reps:
        run_dir = os.path.join(inputdir, "{}_{}vc{}workers".format(workload, vc, worker), str(rep))

        # Get Ping time
        ping_log_path = os.path.join(run_dir, ping_logfile)
        ping_logs = gmts.extract_ping_logs(ping_log_path)
        ping_averages = ping_logs['rtt'].agg(['mean', 'std'])
        # Get MW response time
        middleware_dirs = [dirfuncs.get_only_subdir(os.path.join(run_dir, mw_dir)) for mw_dir in middlewares]
        concatenated_requests = [gmws.concatenate_requestlogs(middleware_dir) for middleware_dir in middleware_dirs]

        metrics = [gmws.extract_metrics(reqs) for reqs in concatenated_requests]

        cut_metrics = [cut.cut_away_warmup_cooldown(mets, 10, 72) for mets in
                       metrics]

        windows = [gmws.aggregate_over_windows(cut_mets) for cut_mets in cut_metrics]

        rep_metrics = gmws.aggregate_over_middlewares(windows)

        all_mw_metrics_per_rep.append(rep_metrics)

        # Get MT response time
        client_logfile_paths = [os.path.join(run_dir, client_logfile) for client_logfile in
                                client_logfiles]
        client_metrics = gmts.aggregate_over_clients(client_logfile_paths)

        cut_client_metrics = cut.cut_away_warmup_cooldown(client_metrics, 10, 72)

        all_mt_metrics_per_rep.append(cut_client_metrics)

    mw_agg_over_reps = gmws.aggregate_over_reps(all_mw_metrics_per_rep)
    mw_averages = gmws.aggregate_over_timesteps(mw_agg_over_reps)
    mt_agg_over_reps = gmts.aggregate_over_reps(all_mt_metrics_per_rep)
    mt_averages = gmts.aggregate_over_timesteps(mt_agg_over_reps)

    metrics = pd.concat([mw_averages, mt_averages, ping_averages], axis=1)



    names = ['NetThread Service Time', 'Queue Time', 'Memcached RTT', 'Worker Service Time',
             'Total Response Time (MW)', 'Ping RTT', 'Total Response Time (MT)']
    metrics.rename({'netthreadServiceTime_ms': names[0], 'queueTime_ms': names[1], 'memcachedRTT_ms': names[2],
                    'workerServiceTime_ms': names[3], 'responseTime_ms': names[4], 'rtt': names[5], 'responsetime': names[6]},
                   axis='columns', inplace=True)
    means = metrics.loc[
        'mean', names]
    stds = metrics.loc[
        'std', names]
    color_cycler = ['#bf812d', '#c7eae5', '#80cdc1', '#01665e', '#003c30', '#dfc27d','#bf812d']
    means.plot(ax=ax, kind='barh', xerr=stds, color=color_cycler)
    ax.set_title("{}, {} clients, {} workers".format(workload, vc*num_threads, worker))
    ax.set_xlabel("Time (msec)")
    ax.set_xlim([0, xlim])

def graph_responsetime(worker, vc, num_threads, workload, middlewares, client_logfiles, reps, inputdir, xlim, ax):

    all_mw_metrics_per_rep = []
    all_mt_metrics_per_rep = []
    for rep in reps:
        run_dir = os.path.join(inputdir, "{}_{}vc{}workers".format(workload, vc, worker), str(rep))

        # Get MW response time
        middleware_dirs = [dirfuncs.get_only_subdir(os.path.join(run_dir, mw_dir)) for mw_dir in middlewares]
        concatenated_requests = [gmws.concatenate_requestlogs(middleware_dir) for middleware_dir in middleware_dirs]

        metrics = [gmws.extract_metrics(reqs) for reqs in concatenated_requests]

        cut_metrics = [cut.cut_away_warmup_cooldown(mets, 10, 72) for mets in
                       metrics]

        windows = [gmws.aggregate_over_windows(cut_mets) for cut_mets in cut_metrics]

        rep_metrics = gmws.aggregate_over_middlewares(windows)

        all_mw_metrics_per_rep.append(rep_metrics)

        # Get MT response time
        client_logfile_paths = [os.path.join(run_dir, client_logfile) for client_logfile in
                                client_logfiles]
        client_metrics = gmts.aggregate_over_clients(client_logfile_paths)

        cut_client_metrics = cut.cut_away_warmup_cooldown(client_metrics, 10, 72)

        all_mt_metrics_per_rep.append(cut_client_metrics)

    mw_agg_over_reps = gmws.aggregate_over_reps(all_mw_metrics_per_rep)
    mw_averages = gmws.aggregate_over_timesteps(mw_agg_over_reps)
    mt_agg_over_reps = gmts.aggregate_over_reps(all_mt_metrics_per_rep)
    mt_averages = gmts.aggregate_over_timesteps(mt_agg_over_reps)

    metrics = pd.concat([mw_averages, mt_averages], axis=1)


    names = ['NetThread Service Time', 'Queue Time', 'Memcached RTT', 'Worker Service Time',
             'Total Response Time (MW)', 'Total Response Time (MT)']
    metrics.rename({'netthreadServiceTime_ms': names[0], 'queueTime_ms': names[1], 'memcachedRTT_ms': names[2], 'workerServiceTime_ms': names[3], 'responseTime_ms': names[4], 'responsetime': names[5]}, axis='columns', inplace=True)
    means = metrics.loc[
        'mean', names]
    stds = metrics.loc[
        'std', names]
    color_cycler = ['#bf812d', '#c7eae5', '#80cdc1', '#01665e', '#003c30','#bf812d']
    means.plot(ax=ax, kind='barh', xerr=stds, color=color_cycler)
    ax.set_title("{}, {} clients, {} workers".format(workload, vc*num_threads, worker))
    ax.set_xlabel("Time (msec)")
    ax.set_xlim([0, xlim])


def get_responsetime_data(worker, vc_settings, num_threads, workload, middlewares, client_logfiles, reps, inputdir):

    all_metrics_per_vc = []
    for vc in vc_settings:
        all_mw_metrics_per_rep = []
        all_mt_metrics_per_rep = []
        for rep in reps:
            run_dir = os.path.join(inputdir, "{}_{}vc{}workers".format(workload, vc, worker), str(rep))

            # Get MW response time
            middleware_dirs = [dirfuncs.get_only_subdir(os.path.join(run_dir, mw_dir)) for mw_dir in middlewares]
            concatenated_requests = [gmws.concatenate_requestlogs(middleware_dir) for middleware_dir in middleware_dirs]

            metrics = [gmws.extract_metrics(reqs) for reqs in concatenated_requests]

            cut_metrics = [cut.cut_away_warmup_cooldown(mets, 10, 72) for mets in
                           metrics]

            windows = [gmws.aggregate_over_windows(cut_mets) for cut_mets in cut_metrics]

            rep_metrics = gmws.aggregate_over_middlewares(windows)

            all_mw_metrics_per_rep.append(rep_metrics)

            # Get MT response time
            client_logfile_paths = [os.path.join(run_dir, client_logfile) for client_logfile in
                                    client_logfiles]
            client_metrics = gmts.aggregate_over_clients(client_logfile_paths)

            cut_client_metrics = cut.cut_away_warmup_cooldown(client_metrics, 10, 72)

            all_mt_metrics_per_rep.append(cut_client_metrics)

        mw_agg_over_reps = gmws.aggregate_over_reps(all_mw_metrics_per_rep)
        mw_averages = gmws.aggregate_over_timesteps(mw_agg_over_reps)
        mt_agg_over_reps = gmts.aggregate_over_reps(all_mt_metrics_per_rep)
        mt_averages = gmts.aggregate_over_timesteps(mt_agg_over_reps)

        metrics = pd.concat([mw_averages, mt_averages], axis=1)
        metrics['num_clients'] = vc * num_threads
        all_metrics_per_vc.append(metrics)

    all_metrics = pd.concat(all_metrics_per_vc)
    #names = ['NetThread Service Time', 'Queue Time', 'Memcached RTT', 'Worker Service Time',
    #         'Total Response Time (MW)', 'Total Response Time (MT)']
    #metrics.rename({'netthreadServiceTime_ms': names[0], 'queueTime_ms': names[1], 'memcachedRTT_ms': names[2], 'workerServiceTime_ms': names[3], 'responseTime_ms': names[4], 'responsetime': names[5]}, axis='columns', inplace=True)
    return all_metrics.loc[
        'mean', :]

def plot_stacked_barchart(data, xtick_labels, ax, ylim):
    data = data.sort_values('num_clients')

    netthread = data['netthreadServiceTime_ms']
    queuetime = data['queueTime_ms']
    workerPreProcessing = data['workerPreProcessingTime_ms']
    memcachedRTT = data['memcachedRTT_ms']
    workerPostProcessing = data['workerPostProcessingTime_ms']
    x = [1, 1.5, 2, 2.5, 3, 3.5, 4, 4.5]
    width = 0.35
    netthreadBar = ax.bar(x=x, width=width, height=netthread, color='#bc80bd')
    queuetimeBar = ax.bar(x=x, width=width, height=queuetime, bottom=netthread, color='#b3de69')
    workerPreBar = ax.bar(x=x, width=width, height=workerPreProcessing, bottom=netthread + queuetime, color='#fdb462')
    memcachedRTTBar = ax.bar(x=x, width=width, height=memcachedRTT, bottom=netthread + queuetime + workerPreProcessing,
                             color='#80b1d3')
    workerPostBar = ax.bar(x=x, width=width, height=workerPostProcessing,
                           bottom=netthread + queuetime + workerPreProcessing + memcachedRTT, color='#fb8072')
    ax.legend((netthreadBar[0], queuetimeBar[0], workerPreBar[0], memcachedRTTBar[0], workerPostBar[0]),
              ('Network Thread', 'Queue Time', 'Worker Preprocessing', 'Memcached RTT', 'Worker Postprocessing'),
              loc='upper left')
    ax.set_xlabel("Number of clients")
    ax.set_ylabel("Response Time (ms)")
    ax.set_ylim([0, ylim])
    plt.xticks(x)
    ax.set_xticklabels(xtick_labels)
import os
import pandas as pd
import gather_middleware_statistics as gmws
import cut_away_warmup_cooldown as cut
import directory_functions as dirfuncs
import matplotlib.pyplot as plt

def graph_queuelength(worker_configs, vc, num_threads, workload, middlewares, reps, inputdir, ylim, ax):

    metrics_per_worker = []
    for worker in worker_configs:
        all_mw_metrics_per_rep = []
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


        mw_agg_over_reps = gmws.aggregate_over_reps(all_mw_metrics_per_rep)
        mw_averages = gmws.aggregate_over_timesteps(mw_agg_over_reps)
        mw_averages['worker'] = worker
        metrics = mw_averages.loc[:, ['queueLength', 'worker']]
        metrics_per_worker.append(metrics)

    metrics = pd.concat(metrics_per_worker)

    names = ['8', '16', '32', '64']
    metrics.rename(index={0: '8', 1: '16', 2: '32', 3: '64'}, inplace=True)
    y = metrics.loc[
        'mean', ['queueLength']].reset_index(drop=True).rename(index={0: '8', 1: '16', 2: '32', 3: '64'})

    stds = metrics.loc[
        'std', ['queueLength']].reset_index(drop=True).rename(index={0: '8', 1: '16', 2: '32', 3: '64'})
    color_cycler = ['#66c2a4', '#41ae76', '#238b45', '#005824']
    y.plot(ax=ax, kind='bar', yerr=stds, color=color_cycler)

    ax.set_title("{}, {} clients".format(workload, vc*num_threads, worker))
    ax.set_xlabel("Workers per Middleware")
    ax.set_ylabel("Average Queue Length")
    ax.set_ylim([0, ylim])
    ax.legend().set_visible(False)
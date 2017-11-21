import os
import pandas as pd
from directory_functions import *

def create_workload_graphs(inputdir, workload, dir_suffix_regex_string, outdir):
    # e.g. dir_suffix_regex_string = "_\d*vc\d*workers"

    print('Input directory is "', inputdir)
    print('Workload is "', workload)
    print('Saving all graphs to "', outdir)

    os.remove(outdir)

    matching_directories = find_workload_dirs(inputdir, workload + dir_suffix_regex_string)

    num_repetitions = get_and_validate_num_repetition(matching_directories)

    loop_function(matching_directories, num_repetitions, outdir)

def loop_function(all_directories, num_repetitions, outdir):

    worker_configurations = find_worker_configurations(all_directories)
    for worker_config in worker_configurations:
        matching_dirs = find_matching_worker_config_dirs(all_directories, worker_config)
        create_workers_config_plots(matching_dirs, num_repetitions, outdir)

#    for experiment_dir in directories_to_loop
#    for experiment_dir in directories_to_loop:
#        num_vc = find_num_vc(experiment_dir)
#        num_workers = find_num_workers(experiment_dir)
#        for rep in range(1, num_repetitions + 1):
#            rep_directory = os.path.join(experiment_dir, str(rep))


def create_workers_config_plots(matching_dirs, num_repetitions, outdir):
    for experiment_dir in matching_dirs:
        num_vc = find_num_vc(experiment_dir)
        for rep in range(1, num_repetitions + 1):
            rep_directory = os.path.join(experiment_dir, str(rep))
            # Go down to the middleware_04 directory and down the only directory
            # There run requests = concatenate_requestlogs(inputdir, "requests")
            # Then sort_by_clock(requests)
            # Then extract_metrics(requests) (be careful that initializeClockTime is already subtracted from first value)
            # Then cut_away_warmup_cooldown
            # Calculate throughput number and add it to all_throughputs
        # We have three throughput values now from the three repetitions
        # Calc avg and std

        ### ANALOGOUS FOR RESP TIME
        # Add to collection for this particular v setting
    # We now have the avg and std xput and avg and std resptime for each vc setting
    # Plot this!

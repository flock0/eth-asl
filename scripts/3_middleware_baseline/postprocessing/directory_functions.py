import re
import os, sys

reps_regex = re.compile("\d*")

def find_workload_dirs(inputdir, regex_string):
    # Gather VCs tested from directory names
    workload_regex = re.compile(regex_string)
    all_directories = filter(lambda x: os.path.isdir(os.path.join(inputdir, x)), os.listdir(inputdir))
    matching_directories = [os.path.join(inputdir, dirname) for dirname in all_directories if workload_regex.match(dirname)]
    return matching_directories

def find_num_repetitions(single_configuration_dir):
    return len([1 for rep_dirname in os.listdir(single_configuration_dir) if reps_regex.match(rep_dirname)])

def get_and_validate_num_repetition(directories_to_check):
    num_repetitions = -1
    for directory in directories_to_check:
        rep_list_len = find_num_repetitions(directory)
        if (num_repetitions == -1):
            num_repetitions = rep_list_len
        else:
            if (rep_list_len != num_repetitions):
                print("Directory {} had {} reps, but expected {}".format(directory, rep_list_len, num_repetitions))
                exit(2)
    if (num_repetitions == -1):
        print("No directies to check have been provided. Cannot validate num of repetitions")
        exit(2)

    return num_repetitions

def find_num_vc(workload_dir_path):
    basedir = os.path.basename(workload_dir_path)
    start_idx = basedir.find("_") + 1 # num of vc occures right after the underscore
    end_idx = basedir.find("vc", start_idx)
    num_vc = int(basedir[start_idx:end_idx])
    return num_vc

def find_num_workers(workload_dir_path):
    basedir = os.path.basename(workload_dir_path)
    start_idx = basedir.find("vc") + 2 # num of workers occures right after vc
    end_idx = basedir.find("workers", start_idx)
    num_workers = int(basedir[start_idx:end_idx])
    return num_workers

def find_worker_configurations(workload_directories):
    all_worker_configurations = []
    for directory in workload_directories:
        num_workers = find_num_workers(directory)
        if num_workers not in all_worker_configurations:
            all_worker_configurations.append(find_num_workers(directory))

    return all_worker_configurations

def find_matching_worker_config_dirs(all_directories, worker_config):
    return [rep_dirname for rep_dirname in all_directories if str(worker_config) + "workers" in rep_dirname]

def get_only_subdir(dirpath):
    filter_dirs = filter(lambda x: os.path.isdir(os.path.join(dirpath, x)), os.listdir(dirpath))
    subdirs = [os.path.join(dirpath, dirname) for dirname in filter_dirs]
    if (len(subdirs) != 1):
        print ("Found more or less than one subdirectories in {}".format(dirpath))
        sys.exit(2)
    return os.path.join(dirpath, subdirs[0])
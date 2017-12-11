import graph_xputresp_vs_vc as xput_vs_vc

# Experiment 3.1

inputdir = "/home/flo/Downloads/exp3/third_exec/3_1_middleware_baseline_onemw_2017-11-23_182327"
experiment_label = "exp3_1"

middlewares = ["middleware_04"]
client_logfiles = ["client_01.log"]
dir_suffix_regex_string = "_\d*vc\d*workers"
warmup_period_endtime = 10
cooldown_period_starttime = 72
outdir = "./graphs"
num_threads = 2
xput_vs_vc.create_workload_graphs(inputdir, experiment_label, "writeOnly", middlewares, client_logfiles, dir_suffix_regex_string, warmup_period_endtime, cooldown_period_starttime, outdir, num_threads)
xput_vs_vc.create_workload_graphs(inputdir, experiment_label, "readOnly", middlewares, client_logfiles, dir_suffix_regex_string, warmup_period_endtime, cooldown_period_starttime, outdir, num_threads)

# Experiment 3.2

inputdir = "/home/flo/Downloads/exp3/third_exec/3_2_middleware_baseline_twomws_2017-11-23_234338"
experiment_label = "exp3_2"

middlewares = ["middleware_04", "middleware_05"]
client_logfiles = ["client_01_0.log", "client_01_1.log"]
dir_suffix_regex_string = "_\d*vc\d*workers"
warmup_period_endtime = 10
cooldown_period_starttime = 72
outdir = "./graphs"
num_threads = 2
xput_vs_vc.create_workload_graphs(inputdir, experiment_label, "writeOnly", middlewares, client_logfiles, dir_suffix_regex_string, warmup_period_endtime, cooldown_period_starttime, outdir, num_threads)
xput_vs_vc.create_workload_graphs(inputdir, experiment_label, "readOnly", middlewares, client_logfiles, dir_suffix_regex_string, warmup_period_endtime, cooldown_period_starttime, outdir, num_threads)
import graph_functions as gf

# Experiment 3.1

inputdir = "/home/flo/Downloads/exp3/first_exec/3_1_middleware_baseline_onemw_2017-11-17_202816"
experiment_label = "exp3_1"

middlewares = ["middleware_04"]
client_logfiles = ["client_01.log"]
dir_suffix_regex_string = "_\d*vc\d*workers"
warmup_period_endtime = 10
cooldown_period_starttime = 72
outdir = "./graphs"

gf.create_workload_graphs(inputdir, experiment_label, "writeOnly", middlewares, client_logfiles, dir_suffix_regex_string, warmup_period_endtime, cooldown_period_starttime, outdir)
gf.create_workload_graphs(inputdir, experiment_label, "readOnly", middlewares, client_logfiles, dir_suffix_regex_string, warmup_period_endtime, cooldown_period_starttime, outdir)

# Experiment 3.2

inputdir = "/home/flo/Downloads/exp3/first_exec/3_2_middleware_baseline_twomws_2017-11-18_004541"
experiment_label = "exp3_2"

middlewares = ["middleware_04", "middleware_05"]
client_logfiles = ["client_01_0.log", "client_01_1.log"]
dir_suffix_regex_string = "_\d*vc\d*workers"
warmup_period_endtime = 10
cooldown_period_starttime = 72
outdir = "./graphs"

gf.create_workload_graphs(inputdir, experiment_label, "writeOnly", middlewares, client_logfiles, dir_suffix_regex_string, warmup_period_endtime, cooldown_period_starttime, outdir)
gf.create_workload_graphs(inputdir, experiment_label, "readOnly", middlewares, client_logfiles, dir_suffix_regex_string, warmup_period_endtime, cooldown_period_starttime, outdir)
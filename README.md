# Advanced Systems Lab 2017
- Author: Florian Chlan
- nethz: fchlan
- Legi: 16-931-933

## Repository Overview
The middleware implementation in Java can be found in the `src` subdirectory.
For some parts of the implementation, unit tests have been written in the `test` directory.
Experiment scripts as well as postprocessing scripts are located in the `scripts` subdirectory.
The experiment data is stored in `logs`.

## Middleware Implementation 
Please see the `src` subdirectory.
### The net classes
- `ClientsSocketHandler`: The "network" thread. Accepts new client connections and handles the reading and parsing of requests
- `MemcachedSocketHandler`: Keeps and handles connections to the memcached servers
- `ReceivalTimers`: Used for keeping part of the timing information in the network thread

### The worker classes
- `Worker`: The Runnable used with the thread pool. Agnostic to request implementations.
- `Request`: Abstract class for a single request. Contains time logging methods
- `GetRequest`: Implementation of a single-key GET request
- `SetRequest`: Implementation of a SET request
- `MultiGetRequest`: Abstract class for multi-key GET requests
- `NonShardedMultiGetRequest`: Implementation of a non-sharded GET request (handled similar to normal GET request)
- `ShardedMultiGetRequest`: Implementation of a sharded GET request
- `RequestFactory`: Parses messages and instantiates Request objects
- `HashingLoadBalancer`: Finds the designated server for GET requests and splits up sharded requests
- `ContextSettingThread(Factory)`: Custom thread for having one CSV log per thread
- `Pair`: Support class

## Experiment Scripts
Please see the `scripts` subdirectory.
General bootstrap-bashscripts are used for setting up the VMs and installing necessary software on them.
For each experiment, a dedicated subdirectory has been created that starts with the number corresponding to the section in the report.
`3_2_rerun_two_clients` is a special case, it's the adapted experiment where two memtier VMs are used instead of one.
Each of these subdirectores (but not `7_queueing_model`) further contains bash scripts for deploying the experiments on a jumphost on azure and then start running it.

The `postprocessing` folder contains Python scripts for extracting data and graphs from the raw logs.
The `*.py` files only contain the functions for extraction. The method invocations are all in the Jupyter notebooks (`*.ipynb`), hence a Jupyter installation is necessary to view and reproduce the graphs.
Oftentimes, the `graphs.ipynb` or `responsetime_stacked_barchart.ipynb` notebooks are the most relevant. In the Jupyter notebooks there is usually an `exp_dir` variable (or `exp_3_2_dir` for example) that needs to be set to the filepath of the extracted raw logfile directory beforehand.


## Experiment Data
The compressed experiment data can be found in the `logs` directory (`exp2`, `exp3`, `exp4`, `exp5`, `exp6`). Not all experiment has been submitted to the git repository due to the 1GB restrictions communicated via e-mail. Data that was deemed only secondary like side-experiments or certain re-runs have been omitted. All data can be found at [this polybox link](https://polybox.ethz.ch/index.php/s/IRzqWxO5kESWMas) (The total size is ~2.4GB).

## Naming of the Log Files
- The raw experiment logs are organized first by configuration, e.g. folder names like `readOnly_32vc16workers` indicate that this directory contains all runs and logs of the read-only workload with 32 virtual clients per thread and 16 workers per middleware.
- Within those directories, there is one directory per repetition (always named `1`, `2` and `3`).
- Each repetition contains `dstat` logs for each client named `client_dstat_0x.log`, where x is {1,2,3}.
- Each repetition contains `memtier` logs for each memtier process named `client_0x_y.log`, where x is {1,2,3} and the optional y is {0,1}. x indicates from which client VM the log originates, y distinguishes the two memtier processes on the client (if there's 1 thread per memtier process).
- Each repetition contains one directory for each middleware involved (`middleware_04` and `middleware_05`, as the MWs were deployed on VM 4 and 5).
- Each middleware directory contains a `dstat.log` and a single timestamped-directory.
- Each of these timestamped middleware subdirectories contains an `mw.log` for general log messages and `requests_Thread-x.csv` files with the request logs for each worker thread individually. x is an arbitrary thread number assigned by Java.

#!/bin/bash

# Parameters
experiment=2_1_baseline_oneserver 

num_repetitions=3
params_vc_per_thread=(1 4 8 12 16 20 24 28 32)

# Setup folder structure
timestamp=$(date +%Y-%m-%d_%H%M%S)
folder_name=$experiment"_"$timestamp
mkdir $folder_name
cd $folder_name

# Wait some time for all VMs to boot
echo "Waiting for VMs to boot up"
sleep 60


# For each repetition
for $rep in $(seq $num_repetitions)
do
	echo "Starting repetition" $rep
	# For each parameter setting
	for vc_per_thread in ${params_vc_per_thread[@]}
	do
		echo "Starting experiment with vc_per_thread="$vc_per_thread
# TODO 			Start memcached, wait shortly
# TODO 			Start middleware, wait shortly
# TODO 			Start memtiers

# TODO 			Wait for experiment duration

# TODO 			Shutdown memtiers (make sure stats are printed, even in case of error or non-completion)
# TODO 			Shutdown Middleware
# TODO 			Shutdown memcached

# TODO 			Copy over logs from memtiers
# TODO 			Copy over logs from middleware
# TODO 			Put logs into right folder structure and rename
	done
done

# TODO 			Zip em up
# TODO 			git commit
# TODO 			push
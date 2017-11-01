#!/bin/bash

# Functions
create_dns_name() {
	echo $nethz"@"$nethz$vm_nameprefix$1$vm_dns_suffix
}
create_vm_ip() {
	echo "10.0.0."$1
}

# Parameters
vm_nameprefix=foraslvms
vm_dns_suffix=.westeurope.cloudapp.azure.com
nethz=fchlan
memcached_port=11211

all_vms=(1 2 3 6)
clients=(1 2 3)
middlewares=()
servers=(6)

experiment=2_1_baseline_oneserver 

num_repetitions=3
params_vc_per_thread=(1 4 8 12 16 20 24 28 32)
params_workload=(readOnly writeOnly)


# Setup folder structure
timestamp=$(date +%Y-%m-%d_%H%M%S)
folder_name=$experiment"_"$timestamp
mkdir $folder_name
cd $folder_name

# Wait some time for all VMs to boot
echo "Waiting for VMs to boot up"
sleep 60

memcached_cmd="memcached -p "$memcached_port" 2>&1 &"

# For each repetition
for $rep in $(seq $num_repetitions)
do
	echo "Starting repetition" $rep
	# For each parameter setting
	for vc_per_thread in ${params_vc_per_thread[@]}
	do
		echo "Starting experiment with vc_per_thread="$vc_per_thread
		for workload in ${params_workload[@]}
		do
			echo "Starting experiment with" $workload "workload"
 			# Start memcached, wait shortly
 			for $mc_id in ${servers[@]}
 			do
 				ssh $(create_dns_name $servers) $memcached_cmd
 			done
			sleep 4
			echo "Started memcached servers"

			# Start memtiers
			ratio=#TODO
			num_clients=#TODO
			num_threads=#TODO
			# Figure out correct command to use by reading the project description
			memtier_cmd = "memtier_benchmark -s "$(create_vm_ip $servers)" -p "$memcached_port" -P memcache_text --run-count=1 --requests=1000000000 --clients="$num_clients" --threads="$num_threads" --expiry-range=9999-10000 --random-data --data-size-range=1-1024 --ratio="$ratio
# TODO 			Wait for experiment duration

# TODO 			Shutdown memtiers (make sure stats are printed, even in case of error or non-completion)
# TODO 			Shutdown Middleware
# TODO 			Shutdown memcached

# TODO 			Copy over logs from memtiers
# TODO 			Copy over logs from middleware
# TODO 			Put logs into right folder structure and rename
		done
	done
done

# TODO 			Zip em up
# TODO 			git commit
# TODO 			push
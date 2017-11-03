#!/bin/bash

# Functions
create_dns_name() {
	echo $nethz"@"$nethz$vm_nameprefix$1$vm_dns_suffix
}
create_vm_ip() {
	echo ${private_ips[$1]}
}

create_client_log_filename() {
	echo "client_0"$1".log"
}

create_server_log_filename() {
	echo "server_0"$1".log"
}
start_all_vms() {
	for server_id in ${all_vms[@]}
	do
		echo "Starting VM" $server_id 
		az vm start --name $vm_nameprefix$server_id --no-wait
	done

	echo "Waiting for VMs to start"

	for server_id in ${all_vms[@]}
	do
		az vm wait --name $vm_nameprefix$server_id --updated
		echo "VM" $server_id "started"
	done
}

### Fixed parameters
resource_group=fchlan
vm_nameprefix=foraslvms
vm_dns_suffix=.westeurope.cloudapp.azure.com
nethz=fchlan
memcached_port=11211
master = 9

### Servers involved in the experiment
all_vms=(1 2 3 6)
clients=(1 2 3)
middlewares=()
servers=(6)

### Experiment parameters
experiment=2_1_baseline_oneserver 
num_repetitions=5
single_experiment_length_sec=90
params_vc_per_thread=(1 4 8 12 16 20 24 28 32)
params_workload=(readOnly writeOnly)

# Configuring the Azure resource group
az configure --defaults group=$resource_group

# Booting up the VMs for this experiment
if [ ! "$#" == 1 ] || [ ! $1 == "nostart" ]; then
	start_all_vms
	# Wait some time for all VMs to boot
	echo "Waiting for VMs to boot up"
	sleep 120
fi

### We're using the private IPs to connect the servers with
### eachother as this is faster than using the public IP address.
echo "Fetching private IPs"
i=0
for vm_id in ${all_vms[@]}
do
	private_ips[${all_vms[$i]}]=$(az vm show --name $vm_nameprefix$vm_id --query privateIps -d --out tsv)
	i=$((i+1))
done

### Setup folder structure for logfiles
timestamp=$(date +%Y-%m-%d_%H%M%S)
folder_name=$experiment"_"$timestamp
mkdir $folder_name
cd $folder_name


### Shutdown memcached that may be running on autostart
for mc_id in ${servers[@]}
do
	ssh $(create_vm_ip $mc_id) sudo service memcached stop
done

echo "Starting experiment" $folder_name
memcached_cmd="nohup memcached -p "$memcached_port" -v > memcached.log 2>&1 &"

# For each repetition
for rep in $(seq $num_repetitions)
do
	echo "    Starting repetition" $rep
	# For each parameter setting
	for vc_per_thread in ${params_vc_per_thread[@]}
	do
		echo "        Starting experiment with vc_per_thread="$vc_per_thread
		for workload in ${params_workload[@]}
		do
			echo "        Starting experiment with" $workload "workload"
 			# Start memcached, wait shortly
 			for mc_id in ${servers[@]}
 			do
 				ssh $(create_vm_ip $mc_id) $memcached_cmd
 			done
			sleep 4
			echo "        Started memcached servers"

			# Start memtiers
			if [ $workload = "writeOnly" ]; then
               ratio=1:0
            elif [ $workload = "readOnly" ]; then
				ratio=0:1
				# Use memtier to fill the servers with keys. As our maximum key is 
				# 10000. We let memtier run for 5 seconds, which should be sufficient.
				memtier_fill_cmd="nohup memtier_benchmark -s "$(create_vm_ip ${servers[0]})" -p "$memcached_port" -P memcache_text --key-maximum=10000 --clients=4 --threads=2 --test-time=5 --expiry-range=9999-10000 --ratio=1:0 > /dev/null 2>&1"
				echo "        Prepare the memcached servers for the read-only workload"
				for server_id in ${servers[@]}
				do
					client_vm_ip=$(create_vm_ip ${clients[0]})
					ssh $nethz"@"$client_vm_ip $memtier_fill_cmd" &"
				done
            fi
            sleep 8

			num_clients=$vc_per_thread
			num_threads=2

			memtier_cmd="nohup memtier_benchmark -s "$(create_vm_ip ${servers[0]})" -p "$memcached_port" -P memcache_text --key-maximum=10000 --clients="$num_clients" --threads="$num_threads" --test-time="$single_experiment_length_sec" --expiry-range=9999-10000 --ratio="$ratio" > memtier.log 2>&1"
			echo "       " $memtier_cmd
			for client_id in ${clients[@]}
			do
				echo "        Starting memtier on client" $client_id
				client_vm_ip=$(create_vm_ip $client_id)
				ssh $nethz"@"$client_vm_ip $memtier_cmd" &"
			done

			# Wait for experiment to finish, + 5 sec to account for delays
			sleep $((single_experiment_length_sec + 5))

			# Shutdown memcached
 			for mc_id in ${servers[@]}
 			do
 				ssh $(create_vm_ip $mc_id) pkill -2f memcached
 			done

 			# Create folder
 			log_dir="./"$workload"_"$vc_per_thread"vc/"$rep
 			mkdir -p $log_dir
 			cd $log_dir
 			echo "        Log dir=" $log_dir
			# Copy over logs from memtiers
			for client_id in ${clients[@]}
			do
				client_vm_ip=$(create_vm_ip $client_id)
				client_log_filename=$(create_client_log_filename $client_id)
				rsync -r $(echo $nethz"@"$client_vm_ip":~/memtier.log") $client_log_filename
				ssh $nethz"@"$client_vm_ip rm memtier.log
			done
			
			# Copy over logs from memcached
 			for mc_id in ${servers[@]}
 			do
 				server_vm_ip=$(create_vm_ip $mc_id)
 				server_log_filename=$(create_server_log_filename $mc_id)
 				rsync -r $(echo $nethz"@"$server_vm_ip":~/memcached.log") $server_log_filename
 				ssh $nethz"@"$server_vm_ip rm memcached.log
 			done

 			cd ../..
 			echo "        ========="
		done
	done
done

# Zip all experiment files
zip_file=$folder_name".tar.gz"
cp ~/experiment.log ./master.log
tar -zcvf $zip_file ./*
mv $zip_file ~/ethz-asl-experiments/
cd ~/ethz-asl-experiments

# Commit experiment data to git repository
git pull
git add $zip_file
git commit -m "Finished experiment $folder_name"
git push

rm $zip_file
cd ..
rm -rf ~/$folder_name

# TODO Shudown all servers
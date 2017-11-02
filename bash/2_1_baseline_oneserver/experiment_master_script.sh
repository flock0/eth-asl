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
# Parameters
vm_nameprefix=foraslvms
vm_dns_suffix=.westeurope.cloudapp.azure.com
nethz=fchlan
memcached_port=11211

all_vms=(1 7 9)
clients=(1)
middlewares=()
servers=(7)

experiment=2_1_baseline_oneserver 

num_repetitions=2
single_experiment_length_sec=10
params_vc_per_thread=(1 2 4)
params_workload=(writeOnly)

echo "Extracting private IPs"
private_ips_path=./private_ips.txt
declare -A private_ips
i=0
while IFS= read -r var
do
  private_ips[${all_vms[$i]}]=$var
  i=$((i+1))
done < "$private_ips_path"

cat $private_ips_path #DEBUG

# Setup folder structure
timestamp=$(date +%Y-%m-%d_%H%M%S)
folder_name=$experiment"_"$timestamp
mkdir $folder_name
cd $folder_name


# Shutdown memcached
for mc_id in ${servers[@]}
do
	ssh $(create_vm_ip $mc_id) sudo service memcached stop
done

echo "Starting experiment" $folder_name
memcached_cmd="memcached -p "$memcached_port" -vv > memcached.log 2>&1 &"

# For each repetition
for rep in $(seq $num_repetitions)
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
 			for mc_id in ${servers[@]}
 			do
 				ssh $(create_vm_ip $mc_id) $memcached_cmd
 				echo $(create_vm_ip $mc_id) $memcached_cmd
 			done
			sleep 4
			echo "Started memcached servers"

			# Start memtiers
			if [ $workload = "writeOnly" ]; then
               ratio=1:0
            elif [ $workload = "readOnly" ]; then
               # TODo Use memtier to fill up the MCs. Write all keys at least once
               ratio=0:1
            fi
			num_clients=$vc_per_thread
			num_threads=2

			memtier_cmd="memtier_benchmark -s "$(create_vm_ip $servers)" -p "$memcached_port" -P memcache_text --key-maximum=10000 --clients="$num_clients" --threads="$num_threads" --test-time="$single_experiment_length_sec" --expiry-range=9999-10000 --ratio="$ratio" > memtier.log"
			echo $memtier_cmd
			for client_id in ${clients[@]}
			do
				echo "Starting memtier on client" $client_id
				client_vm_ip=$(create_vm_ip $client_id)
				echo $nethz"@"$client_vm_ip $memtier_cmd" &"
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
 			echo "Log dir=" $log_dir
			# Copy over logs from memtiers
			for client_id in ${clients[@]}
			do
				client_vm_ip=$(create_vm_ip $client_id)
				client_log_filename=$(create_client_log_filename $client_id)
				echo "client_log_filename" $client_log_filename
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
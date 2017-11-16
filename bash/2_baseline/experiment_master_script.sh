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

create_client_dstat_filename() {
	echo "client_dstat_0"$1".log"
}

create_client_ping_filename() {
	echo "client_ping_0"$1".log"
}

create_server_log_filename() {
	echo "server_0"$1".log"
}

create_server_dstat_filename() {
	echo "server_dstat_0"$1".log"
}

start_all_vms() {
	for server_id in ${all_exp_vms[@]}
	do
		echo "Starting VM" $server_id 
		az vm start --name $vm_nameprefix$server_id --no-wait
	done

	echo "Waiting for VMs to start"

	for server_id in ${all_exp_vms[@]}
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
master=9


######################
### PRE-EXP SETUP  ###
######################
### Servers involved in the whole experiment
all_exp_vms=(1 2 3 6 7)

# Booting up the VMs for this experiment
if [ ! "$#" == 1 ] || [ ! $1 == "nostart" ]; then
	start_all_vms
	# Wait some time for all VMs to boot
	echo "Sleeping for 120 sec to let VMs services boot up..."
	sleep 60
	echo "60 seconds remaining..."
	sleep 60
fi

# Configuring the Azure resource group
az configure --defaults group=$resource_group

######################
### EXPERIMENT 2_1 ###
######################

### Servers involved in experiment 2_1
all_vms=(1 2 3 6)
clients=(1 2 3)
middlewares=()
servers=(6)

### Experiment parameters
experiment=2_1_baseline_oneserver 
num_repetitions=3
single_experiment_length_sec=82
params_vc_per_thread=(1 4 8 16 32 64 128 256)
params_workload=(writeOnly readOnly)
num_threads=2

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
	ssh $(create_vm_ip $mc_id) "sudo service memcached stop; pkill -2f memcached"
done

### Start up all instances of memcached and prepopulate them for the read-only workload
memcached_cmd="> dstat.log; nohup dstat -cdlmnyt --output dstat.log 5 > /dev/null &
			   nohup memcached -p "$memcached_port" -t 1 -v > memcached.log 2>&1 &"
for mc_id in ${servers[@]}
do
	ssh $(create_vm_ip $mc_id) $memcached_cmd
done
sleep 4
echo "Started memcached servers"

sleep $((fill_time_sec + 2))
### Now start with the actual experiments
echo "========="
echo "Starting experiment" $folder_name

for workload in ${params_workload[@]}
do
	echo "    Starting experiment with" $workload "workload"
	for rep in $(seq $num_repetitions)
	do
		echo "        Starting repetition" $rep
		for vc_per_thread in ${params_vc_per_thread[@]}
		do
			echo "        Starting experiment with vc_per_thread="$vc_per_thread
			if [ $workload = "writeOnly" ]; then
               ratio=1:0
            elif [ $workload = "readOnly" ]; then
				ratio=0:1
			fi
			target_server_ip=$(create_vm_ip ${servers[0]})
			memtier_cmd="> dstat.log; > ping.log;
						echo $(date +%Y%m%d_%H%M%S) > memtier.log;
						echo $(date +%Y%m%d_%H%M%S) > ping.log;
						nohup dstat -cdlmnyt --output dstat.log 5 > /dev/null &
						ping -Di 5 "$target_server_ip" -w "$single_experiment_length_sec" > ping.log &
						nohup memtier_benchmark -s "$target_server_ip" -p "$memcached_port" -P memcache_text --key-maximum=10000 --clients="$vc_per_thread" 
						--threads="$num_threads" --test-time="$single_experiment_length_sec" --expiry-range=9999-10000 --data-size=1024 --ratio="$ratio" > memtier.log 2>&1"
			for client_id in ${clients[@]}
			do
				echo "        Starting memtier on client" $client_id
				client_vm_ip=$(create_vm_ip $client_id)
				ssh $nethz"@"$client_vm_ip $memtier_cmd" &"
			done

			# Wait for experiment to finish, + 5 sec to account for delays
			sleep $((single_experiment_length_sec + 5))

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
				client_dstat_filename=$(create_client_dstat_filename $client_id)
				client_ping_filename=$(create_client_ping_filename $client_id)
				ssh $nethz"@"$client_vm_ip pkill -f dstat
				rsync -r $(echo $nethz"@"$client_vm_ip":~/memtier.log") $client_log_filename
				rsync -r $(echo $nethz"@"$client_vm_ip":~/dstat.log") $client_dstat_filename
				rsync -r $(echo $nethz"@"$client_vm_ip":~/ping.log") $client_ping_filename
				ssh $nethz"@"$client_vm_ip rm memtier.log
			done

 			cd ../..
 			echo "        ========="
		done
	done
done

# Shutdown all memcached servers
for mc_id in ${servers[@]}
do
	ssh $(create_vm_ip $mc_id) pkill -2f memcached; pkill -f dstat
done

# Copy over logs from memcached
for mc_id in ${servers[@]}
do
	server_vm_ip=$(create_vm_ip $mc_id)
	server_log_filename=$(create_server_log_filename $mc_id)
	server_dstat_filename=$(create_server_dstat_filename $mc_id)
	rsync -r $(echo $nethz"@"$server_vm_ip":~/memcached.log") ~/$folder_name/$server_log_filename
	rsync -r $(echo $nethz"@"$server_vm_ip":~/dstat.log") ~/$folder_name/$server_dstat_filename
	ssh $nethz"@"$server_vm_ip rm memcached.log
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

cd
rm -rf ~/$folder_name



######################
### EXPERIMENT 2_2 ###
######################

### Servers involved in experiment 2_2
all_vms=(1 6 7)
clients=(1)
middlewares=()
servers=(6 7)

### Experiment parameters
experiment=2_2_baseline_twoservers
num_repetitions=3
single_experiment_length_sec=82
params_vc_per_thread=(1 4 8 16 32 64 128 256 512)
params_workload=(writeOnly readOnly)
num_threads=1

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
	ssh $(create_vm_ip $mc_id) "sudo service memcached stop; pkill -2f memcached"
done

### Start up all instances of memcached and prepopulate them for the read-only workload
memcached_cmd="> dstat.log; nohup dstat -cdlmnyt --output dstat.log 5 > /dev/null & nohup memcached -p "$memcached_port" -t 1 -v > memcached.log 2>&1 &"
for mc_id in ${servers[@]}
do
	ssh $(create_vm_ip $mc_id) $memcached_cmd
done
sleep 4
echo "Started memcached servers"

sleep $((fill_time_sec + 2))
### Now start with the actual experiments
echo "========="
echo "Starting experiment" $folder_name

for workload in ${params_workload[@]}
do
	echo "    Starting experiment with" $workload "workload"
	for rep in $(seq $num_repetitions)
	do
		echo "        Starting repetition" $rep
		for vc_per_thread in ${params_vc_per_thread[@]}
		do
			echo "        Starting experiment with vc_per_thread="$vc_per_thread
 			
			if [ $workload = "writeOnly" ]; then
               ratio=1:0
            elif [ $workload = "readOnly" ]; then
				ratio=0:1
			fi

			memtier_0_cmd="> dstat.log; > ping_0.log;
			               echo $(date +%Y%m%d_%H%M%S) > memtier_0.log;
			               echo $(date +%Y%m%d_%H%M%S) > ping.log;
			               nohup dstat -cdlmnyt --output dstat.log 5 > /dev/null &
			               ping -Di 5 "$(create_vm_ip ${servers[0]})" -w "$single_experiment_length_sec" > ping_0.log &
			               nohup memtier_benchmark -s "$(create_vm_ip ${servers[0]})" -p "$memcached_port" -P memcache_text --key-maximum=10000 --clients="$vc_per_thread" 
			               --threads="$num_threads" --test-time="$single_experiment_length_sec" --expiry-range=9999-10000 --data-size=1024 --ratio="$ratio" > memtier_0.log 2>&1"

			memtier_1_cmd="> ping_1.log;
						   echo $(date +%Y%m%d_%H%M%S) > memtier_1.log;
						   ping -Di 5 "$(create_vm_ip ${servers[1]})" -w "$single_experiment_length_sec" > ping_1.log &
			               nohup memtier_benchmark -s "$(create_vm_ip ${servers[1]})" -p "$memcached_port" -P memcache_text --key-maximum=10000 --clients="$vc_per_thread" 
			               --threads="$num_threads" --test-time="$single_experiment_length_sec" --expiry-range=9999-10000 --data-size=1024 --ratio="$ratio" > memtier_1.log 2>&1"

			echo "       " $memtier_0_cmd
			echo "       " $memtier_1_cmd
			client_id=${clients[0]}
			echo "        Starting memtier on client" $client_id
			client_vm_ip=$(create_vm_ip $client_id)
			ssh $nethz"@"$client_vm_ip $memtier_0_cmd" &"
			ssh $nethz"@"$client_vm_ip $memtier_1_cmd" &"

			# Wait for experiment to finish, + 5 sec to account for delays
			sleep $((single_experiment_length_sec + 5))

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
				client_dstat_filename=$(create_client_dstat_filename $client_id)
				client_ping_filename=$(create_client_ping_filename $client_id)
				ssh $nethz"@"$client_vm_ip pkill -f dstat
				rsync -r $(echo $nethz"@"$client_vm_ip":~/memtier_0.log") "client_01_0.log"
				rsync -r $(echo $nethz"@"$client_vm_ip":~/memtier_1.log") "client_01_1.log"
				rsync -r $(echo $nethz"@"$client_vm_ip":~/dstat.log") $client_dstat_filename
				rsync -r $(echo $nethz"@"$client_vm_ip":~/ping_0.log") "client_ping_01_0.log"
				rsync -r $(echo $nethz"@"$client_vm_ip":~/ping_1.log") "client_ping_01_1.log"
				ssh $nethz"@"$client_vm_ip rm memtier_0.log memtier_1.log
			done

 			cd ../..
 			echo "        ========="
		done
	done
done

# Shutdown all memcached servers
for mc_id in ${servers[@]}
do
	ssh $(create_vm_ip $mc_id) pkill -2f memcached; pkill -f dstat
done

# Copy over logs from memcached
for mc_id in ${servers[@]}
do
	server_vm_ip=$(create_vm_ip $mc_id)
	server_log_filename=$(create_server_log_filename $mc_id)
	server_dstat_filename=$(create_server_dstat_filename $mc_id)
	rsync -r $(echo $nethz"@"$server_vm_ip":~/memcached.log") ~/$folder_name/$server_log_filename
	rsync -r $(echo $nethz"@"$server_vm_ip":~/dstat.log") ~/$folder_name/$server_dstat_filename
	ssh $nethz"@"$server_vm_ip rm memcached.log
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

cd
rm -rf ~/$folder_name






### Shudown all servers
for server_id in ${all_exp_vms[@]}
do
	echo "Stopping VM" $server_id 
	az vm deallocate --name $vm_nameprefix$server_id --no-wait
done
az vm deallocate --name $vm_nameprefix$master --no-wait
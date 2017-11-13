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

create_middleware_logs_dirname() {
	echo "middleware_0"$1
}

create_middleware_dstat_filename() {
	echo "middleware_dstat_0"$1".log"	
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
middleware_port=11211
master=9


######################
### PRE-EXP SETUP  ###
######################
### Servers involved in the whole experiment
all_exp_vms=(1 4 5 6) # for real experiment (1 4 5 6)

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
### EXPERIMENT 3_1 ###
######################

### Servers involved in experiment 3_1
all_vms=(1 4 6)
clients=(1)
middlewares=(4)
servers=(6)

### Experiment parameters
experiment=3_1_middleware_baseline_onemw
num_repetitions=1 # for real experiment 3
single_experiment_length_sec=10 # for real experiment 82
params_vc_per_thread=(2) # for real experiment (1 2 4 8 16 32)
params_workload=(writeOnly readOnly)
params_num_workers_per_mw=(8) # for real experiment (8 16 32 64)
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

### Start up all instances of memcached
memcached_cmd="> dstat.log; nohup dstat -cdlmnyt --output dstat.log 5 > /dev/null &
			   nohup memcached -p "$memcached_port" -v > memcached.log 2>&1 &"
for mc_id in ${servers[@]}
do
	ssh $(create_vm_ip $mc_id) $memcached_cmd
done
sleep 4
echo "Started memcached servers"

### Shutdown middleware instances that may be still running
for mw_id in ${middlewares[@]}
do
	ssh $(create_vm_ip $mw_id) pkill --signal=SIGTERM -f java
done

### Compile middleware 
middleware_start_cmd="cd asl-fall17-project/; git checkout develop; git pull; ant clean; ant jar > build.log; rm -rf logs/*"

for mw_id in ${middlewares[@]}
do
	ssh $(create_vm_ip $mw_id) $middleware_start_cmd
done
sleep 4
echo "Middlewares compiled"


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
			echo "            Starting experiment with vc_per_thread="$vc_per_thread
			if [ $workload = "writeOnly" ]; then
               ratio=1:0
            elif [ $workload = "readOnly" ]; then
				ratio=0:1
			fi

			for num_workers in ${params_num_workers_per_mw[@]}
			do
				echo "                Starting experiment with num_workers="$num_workers
				# Start middlewares

			    for mw_id in ${middlewares[@]}
				do
					middleware_cmd="> dstat.log; nohup dstat -cdlmnyt --output dstat.log 5 > /dev/null & cd asl-fall17-project;
			                        nohup java -jar bin/jar/ASL17_Middleware.jar -l "$(create_vm_ip $mw_id)" -p "$middleware_port" -t "$num_workers" 
			                        -s false -m "$(create_vm_ip ${servers[0]})":"$memcached_port" > /dev/null &"
					ssh $(create_vm_ip $mw_id) $middleware_cmd
				done
				sleep 4
				echo "                Middlewares started"

				
				target_middleware_ip=$(create_vm_ip ${middlewares[0]})
				memtier_cmd="> dstat.log; > ping.log;
							echo $(date +%Y%m%d_%H%M%S) > memtier.log;
							echo $(date +%Y%m%d_%H%M%S) > ping.log;
							nohup dstat -cdlmnyt --output dstat.log 5 > /dev/null &
							ping -Di 5 "$target_middleware_ip" -w "$single_experiment_length_sec" > ping.log &
							nohup memtier_benchmark -s "$target_middleware_ip" -p "$middleware_port" -P memcache_text --key-maximum=10000 --clients="$vc_per_thread" 
							--threads="$num_threads" --test-time="$single_experiment_length_sec" --expiry-range=9999-10000 --data-size=1024 --ratio="$ratio" > memtier.log 2>&1"
				for client_id in ${clients[@]}
				do
					echo "                Starting memtier on client" $client_id
					client_vm_ip=$(create_vm_ip $client_id)
					ssh $nethz"@"$client_vm_ip $memtier_cmd" &"
				done

				# Wait for experiment to finish, + 5 sec to account for delays
				sleep $((single_experiment_length_sec + 5))

				# Terminate middlewares
				for mw_id in ${middlewares[@]}
				do
					ssh $(create_vm_ip $mw_id) pkill --signal=SIGTERM -f java
				done
				sleep 4
				echo "                Middlewares stopped"


	 			# Create folder
	 			log_dir="./"$workload"_"$vc_per_thread"vc"$num_workers"workers/"$rep
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

				# Copy over logs from middlewares. (Should only contain one log folder)
				# Afterwards remove the log folder
				for mw_id in ${middlewares[@]}
				do
					middleware_vm_ip=$(create_vm_ip $mw_id)
					middleware_logs_dirname=$(create_middleware_logs_dirname $mw_id)
					middleware_dstat_filename=$(create_middleware_dstat_filename $mw_id)
					ssh $nethz"@"$middleware_vm_ip pkill -f dstat
					rsync -r $(echo $nethz"@"$middleware_vm_ip":~/asl-fall17-project/logs/*") $middleware_logs_dirname"/"
					rsync -r $(echo $nethz"@"$middleware_vm_ip":~/dstat.log") $middleware_dstat_filename
					ssh $nethz"@"$middleware_vm_ip rm -rf ~/asl-fall17-project/logs/*
				done
				sleep 4

	 			cd ../..
	 			echo "        ========="
			done
			
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

rm $zip_file
cd
rm -rf ~/$folder_name

######################
### EXPERIMENT 3_2 ###
######################

### Servers involved in experiment 3_2
all_vms=(1 4 5 6)
clients=(1)
middlewares=(4 5)
servers=(6)

### Experiment parameters
experiment=3_2_middleware_baseline_twomws
num_repetitions=1 # for real experiment 3
single_experiment_length_sec=10 # for real experiment 82
params_vc_per_thread=(2) # for real experiment (1 2 4 8 16 32)
params_workload=(writeOnly readOnly)
params_num_workers_per_mw=(8) # for real experiment (8 16 32 64)
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

### Start up all instances of memcached
memcached_cmd="> dstat.log; nohup dstat -cdlmnyt --output dstat.log 5 > /dev/null &
			   nohup memcached -p "$memcached_port" -v > memcached.log 2>&1 &"
for mc_id in ${servers[@]}
do
	ssh $(create_vm_ip $mc_id) $memcached_cmd
done
sleep 4
echo "Started memcached servers"

### Shutdown middleware instances that may be still running
for mw_id in ${middlewares[@]}
do
	ssh $(create_vm_ip $mw_id) pkill --signal=SIGTERM -f java
done

### Compile middleware 
middleware_start_cmd="cd asl-fall17-project/; git checkout develop; git pull; ant clean; ant jar > build.log; rm -rf logs/*"

for mw_id in ${middlewares[@]}
do
	ssh $(create_vm_ip $mw_id) $middleware_start_cmd
done
sleep 4
echo "Middlewares compiled"


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
			echo "            Starting experiment with vc_per_thread="$vc_per_thread
			if [ $workload = "writeOnly" ]; then
               ratio=1:0
            elif [ $workload = "readOnly" ]; then
				ratio=0:1
			fi

			for num_workers in ${params_num_workers_per_mw[@]}
			do
				echo "                Starting experiment with num_workers="$num_workers
				# Start middlewares

			    for mw_id in ${middlewares[@]}
				do
					middleware_cmd="> dstat.log; nohup dstat -cdlmnyt --output dstat.log 5 > /dev/null & cd asl-fall17-project;
			                        nohup java -jar bin/jar/ASL17_Middleware.jar -l "$(create_vm_ip $mw_id)" -p "$middleware_port" -t "$num_workers" 
			                        -s false -m "$(create_vm_ip ${servers[0]})":"$memcached_port" > /dev/null &"
					ssh $(create_vm_ip $mw_id) $middleware_cmd
				done
				sleep 4
				echo "                Middlewares started"

				
				target_middleware_0_ip=$(create_vm_ip ${middlewares[0]})
				target_middleware_1_ip=$(create_vm_ip ${middlewares[1]})
				memtier_0_cmd="> dstat.log; > ping.log;
							echo $(date +%Y%m%d_%H%M%S) > memtier_0.log;
							echo $(date +%Y%m%d_%H%M%S) > ping.log;
							nohup dstat -cdlmnyt --output dstat.log 5 > /dev/null &
							ping -Di 5 "$target_middleware_0_ip" -w "$single_experiment_length_sec" > ping.log &
							nohup memtier_benchmark -s "$target_middleware_0_ip" -p "$middleware_port" -P memcache_text --key-maximum=10000 --clients="$vc_per_thread" 
							--threads="$num_threads" --test-time="$single_experiment_length_sec" --expiry-range=9999-10000 --data-size=1024 --ratio="$ratio" > memtier_0.log 2>&1"

				memtier_1_cmd="echo $(date +%Y%m%d_%H%M%S) > memtier_1.log;
							nohup memtier_benchmark -s "$target_middleware_1_ip" -p "$middleware_port" -P memcache_text --key-maximum=10000 --clients="$vc_per_thread" 
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
				#######################################
				### TODO Continue adapting script here!
				#######################################
				# Terminate middlewares
				for mw_id in ${middlewares[@]}
				do
					ssh $(create_vm_ip $mw_id) pkill --signal=SIGTERM -f java
				done
				sleep 4
				echo "                Middlewares stopped"


	 			# Create folder
	 			log_dir="./"$workload"_"$vc_per_thread"vc"$num_workers"workers/"$rep
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

				# Copy over logs from middlewares. (Should only contain one log folder)
				# Afterwards remove the log folder
				for mw_id in ${middlewares[@]}
				do
					middleware_vm_ip=$(create_vm_ip $mw_id)
					middleware_logs_dirname=$(create_middleware_logs_dirname $mw_id)
					middleware_dstat_filename=$(create_middleware_dstat_filename $mw_id)
					ssh $nethz"@"$middleware_vm_ip pkill -f dstat
					rsync -r $(echo $nethz"@"$middleware_vm_ip":~/asl-fall17-project/logs/*") $middleware_logs_dirname"/"
					rsync -r $(echo $nethz"@"$middleware_vm_ip":~/dstat.log") $middleware_dstat_filename
					ssh $nethz"@"$middleware_vm_ip rm -rf ~/asl-fall17-project/logs/*
				done
				sleep 4

	 			cd ../..
	 			echo "        ========="
			done
			
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

rm $zip_file
cd
rm -rf ~/$folder_name

### Shudown all servers
for server_id in ${all_exp_vms[@]}
do
	echo "Stopping VM" $server_id 
	az vm deallocate --name $vm_nameprefix$server_id --no-wait
done
az vm deallocate --name $vm_nameprefix$master --no-wait
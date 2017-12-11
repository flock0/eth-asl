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
memcached_port=12345
middleware_port=12346
master=9


######################
### PRE-EXP SETUP  ###
######################
### Servers involved in the whole experiment
all_exp_vms=(1 4 5 6) # for real experiment (1 4 5 6)

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
num_repetitions=3
single_experiment_length_sec=82
params_vc_per_thread=(1 8 16 32 64 96 128 160)
params_workload=(writeOnly readOnly)
params_num_workers_per_mw=(8 16 32 64)
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
memcached_cmd="> dstat.log; nohup dstat -cdlmnyt --output dstat.log 1 > /dev/null &
			   nohup memcached -p "$memcached_port" -t 1 -v > memcached.log 2>&1 &"
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

for mc_id in ${servers[@]}
do
	memtier_fill_cmd="memtier_benchmark -s "$(create_vm_ip $mc_id)" -p "$memcached_port" -P memcache_text --key-maximum=10000 --clients=1 
						--threads=1 --expiry-range=86400-86401 --data-size=1024 --ratio=1:0  --key-pattern=S:S > /dev/null 2>&1"
	ssh $(create_vm_ip ${clients[0]}) $memtier_fill_cmd
done
echo "Prepopulated memcached servers"

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
				echo $(date +"                Timestamp %H:%M:%S")
				# Start middlewares

			    for mw_id in ${middlewares[@]}
				do
					middleware_cmd="> dstat.log; nohup dstat -cdlmnyt --output dstat.log 1 > /dev/null & cd asl-fall17-project;
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
							nohup dstat -cdlmnyt --output dstat.log 1 > /dev/null &
							ping -Di 1 "$target_middleware_ip" -w "$single_experiment_length_sec" > ping.log &
							nohup memtier_benchmark -s "$target_middleware_ip" -p "$middleware_port" -P memcache_text --key-maximum=10000 --clients="$vc_per_thread" 
							--threads="$num_threads" --test-time="$single_experiment_length_sec" --expiry-range=86400-86401 --data-size=1024 --ratio="$ratio" > memtier.log 2>&1"
				for client_id in ${clients[@]}
				do
					echo "                Starting memtier on client" $client_id
					client_vm_ip=$(create_vm_ip $client_id)
					ssh $nethz"@"$client_vm_ip $memtier_cmd" &"
				done

				# Wait for experiment to finish, + 5 sec to account for delays
				sleep $((single_experiment_length_sec + 4))

				# Terminate middlewares
				for mw_id in ${middlewares[@]}
				do
					ssh $(create_vm_ip $mw_id) pkill --signal=SIGTERM -f java
				done
				sleep 2
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
					ssh $nethz"@"$middleware_vm_ip pkill -f dstat
					rsync -r $(echo $nethz"@"$middleware_vm_ip":~/asl-fall17-project/logs/*") $middleware_logs_dirname"/"
					rsync -r $(echo $nethz"@"$middleware_vm_ip":~/dstat.log") $middleware_logs_dirname"/dstat.log"
					ssh $nethz"@"$middleware_vm_ip rm -rf ~/asl-fall17-project/logs/*
				done

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
tar -zcf $zip_file ./*
mv $zip_file ~/ethz-asl-experiments/
cd ~/ethz-asl-experiments

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
num_repetitions=3
single_experiment_length_sec=82
params_vc_per_thread=(1 8 16 32 64 96 128 160)
params_workload=(writeOnly readOnly)
params_num_workers_per_mw=(8 16 32 64)
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
memcached_cmd="> dstat.log; nohup dstat -cdlmnyt --output dstat.log 1 > /dev/null &
			   nohup memcached -p "$memcached_port" -t 1 -v > memcached.log 2>&1 &"
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

for mc_id in ${servers[@]}
do
	memtier_fill_cmd="memtier_benchmark -s "$(create_vm_ip $mc_id)" -p "$memcached_port" -P memcache_text --key-maximum=10000 --clients=1 
						--threads=1 --expiry-range=86400-86401 --data-size=1024 --ratio=1:0  --key-pattern=S:S > /dev/null 2>&1"
	ssh $(create_vm_ip ${clients[0]}) $memtier_fill_cmd
done
echo "Prepopulated memcached servers"

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
				echo $(date +"                Timestamp %H:%M:%S")
				# Start middlewares

			    for mw_id in ${middlewares[@]}
				do
					middleware_cmd="> dstat.log; nohup dstat -cdlmnyt --output dstat.log 1 > /dev/null & cd asl-fall17-project;
			                        nohup java -jar bin/jar/ASL17_Middleware.jar -l "$(create_vm_ip $mw_id)" -p "$middleware_port" -t "$num_workers" 
			                        -s false -m "$(create_vm_ip ${servers[0]})":"$memcached_port" > /dev/null &"
					ssh $(create_vm_ip $mw_id) $middleware_cmd
				done
				sleep 4
				echo "                Middlewares started"

				
				target_middleware_0_ip=$(create_vm_ip ${middlewares[0]})
				target_middleware_1_ip=$(create_vm_ip ${middlewares[1]})
				memtier_0_cmd="> dstat.log; > ping_0.log;
							echo $(date +%Y%m%d_%H%M%S) > memtier_0.log;
							echo $(date +%Y%m%d_%H%M%S) > ping_0.log;
							nohup dstat -cdlmnyt --output dstat.log 1 > /dev/null &
							ping -Di 1 "$target_middleware_0_ip" -w "$single_experiment_length_sec" > ping_0.log &
							nohup memtier_benchmark -s "$target_middleware_0_ip" -p "$middleware_port" -P memcache_text --key-maximum=10000 --clients="$vc_per_thread" 
							--threads="$num_threads" --test-time="$single_experiment_length_sec" --expiry-range=86400-86401 --data-size=1024 --ratio="$ratio" > memtier_0.log 2>&1"

				memtier_1_cmd="> ping_1.log;
							echo $(date +%Y%m%d_%H%M%S) > memtier_1.log;
							echo $(date +%Y%m%d_%H%M%S) > ping_1.log;
							ping -Di 1 "$target_middleware_1_ip" -w "$single_experiment_length_sec" > ping_1.log &
							nohup memtier_benchmark -s "$target_middleware_1_ip" -p "$middleware_port" -P memcache_text --key-maximum=10000 --clients="$vc_per_thread" 
							--threads="$num_threads" --test-time="$single_experiment_length_sec" --expiry-range=86400-86401 --data-size=1024 --ratio="$ratio" > memtier_1.log 2>&1"
				
				echo "       " $memtier_0_cmd
				echo "       " $memtier_1_cmd
				client_id=${clients[0]}
				echo "        Starting memtier on client" $client_id
				client_vm_ip=$(create_vm_ip $client_id)
				ssh $nethz"@"$client_vm_ip $memtier_0_cmd" &"
				ssh $nethz"@"$client_vm_ip $memtier_1_cmd" &"

				# Wait for experiment to finish, + 5 sec to account for delays
				sleep $((single_experiment_length_sec + 4))
				
				# Terminate middlewares
				for mw_id in ${middlewares[@]}
				do
					ssh $(create_vm_ip $mw_id) pkill --signal=SIGTERM -f java
				done
				sleep 2
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
					client_dstat_filename=$(create_client_dstat_filename $client_id)
					ssh $nethz"@"$client_vm_ip pkill -f dstat
					rsync -r $(echo $nethz"@"$client_vm_ip":~/memtier_0.log") "client_01_0.log"
					rsync -r $(echo $nethz"@"$client_vm_ip":~/memtier_1.log") "client_01_1.log"
					rsync -r $(echo $nethz"@"$client_vm_ip":~/dstat.log") $client_dstat_filename
					rsync -r $(echo $nethz"@"$client_vm_ip":~/ping_0.log") "client_ping_01_0.log"
					rsync -r $(echo $nethz"@"$client_vm_ip":~/ping_1.log") "client_ping_01_1.log"
					ssh $nethz"@"$client_vm_ip rm memtier_0.log memtier_1.log &
				done

				# Copy over logs from middlewares. (Should only contain one log folder)
				# Afterwards remove the log folder
				for mw_id in ${middlewares[@]}
				do
					middleware_vm_ip=$(create_vm_ip $mw_id)
					middleware_logs_dirname=$(create_middleware_logs_dirname $mw_id)
					ssh $nethz"@"$middleware_vm_ip pkill -f dstat
					rsync -r $(echo $nethz"@"$middleware_vm_ip":~/asl-fall17-project/logs/*") $middleware_logs_dirname"/"
					rsync -r $(echo $nethz"@"$middleware_vm_ip":~/dstat.log") $middleware_logs_dirname"/dstat.log"
					ssh $nethz"@"$middleware_vm_ip rm -rf ~/asl-fall17-project/logs/*
				done

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
tar -zcf $zip_file ./*
mv $zip_file ~/ethz-asl-experiments/
cd ~/ethz-asl-experiments

cd
rm -rf ~/$folder_name

### Shudown all servers
for server_id in ${all_exp_vms[@]}
do
	echo "Stopping VM" $server_id 
	az vm deallocate --name $vm_nameprefix$server_id --no-wait
done
az vm deallocate --name $vm_nameprefix$master --no-wait
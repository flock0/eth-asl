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

create_pssh_host_files() {
	touch $clients_host_file
	> $clients_host_file
	for vm_id in ${clients[@]}
	do
		echo $nethz"@"$(create_vm_ip $vm_id) >> $clients_host_file
	done

	touch $clients_host_file
	> $middlewares_host_file
	for vm_id in ${middlewares[@]}
	do
		echo $nethz"@"$(create_vm_ip $vm_id) >> $middlewares_host_file
	done

	touch $clients_host_file
	> $servers_host_file
	for vm_id in ${servers[@]}
	do
		echo $nethz"@"$(create_vm_ip $vm_id) >> $servers_host_file
	done

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
clients_host_file=~/client_hosts
middlewares_host_file=~/middleware_hosts
servers_host_file=~/server_hosts

######################
### PRE-EXP SETUP  ###
######################
### Servers involved in the whole experiment
all_exp_vms=(1 2 3 4 5 6 7 8) # for real experiment (1 4 5 6)

# Booting up the VMs for this experiment

# Configuring the Azure resource group
az configure --defaults group=$resource_group

######################
### EXPERIMENT 4   ###
######################

### Servers involved in experiment 4
all_vms=(1 2 3 4 5 6 7 8)
clients=(1 2 3)
middlewares=(4 5)
servers=(6 7 8)

### Experiment parameters
experiment=6_2k
num_repetitions=3
single_experiment_length_sec=82
vc_per_thread=32
params_workload=(writeOnly readOnly fiftyFifty)
params_num_workers_per_mw=(8 32)
params_num_middlewares=(1 2)
params_num_memcached=(2 3)
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

create_pssh_host_files

### Setup folder structure for logfiles
timestamp=$(date +%Y-%m-%d_%H%M%S)
folder_name=$experiment"_"$timestamp
mkdir $folder_name
cd $folder_name


### Shutdown memcached that may be running on autostart
parallel-ssh -i -O StrictHostKeyChecking=no -h $servers_host_file "sudo service memcached stop; pkill -2f memcached"

### Start up all instances of memcached
memcached_cmd="sudo service memcached stop; pkill -2f memcached; > dstat.log; nohup dstat -cdlmnyt --output dstat.log 1 > /dev/null &
			   nohup memcached -p "$memcached_port" -t 1 -v > memcached.log 2>&1 &"
parallel-ssh -i -O StrictHostKeyChecking=no -h $servers_host_file $memcached_cmd
sleep 4
echo "Started memcached servers"

### Shutdown middleware instances that may be still running
parallel-ssh -i -O StrictHostKeyChecking=no -h $middlewares_host_file pkill --signal=SIGTERM -f java

### Compile middleware 
middleware_start_cmd="cd asl-fall17-project/; git checkout develop; git pull; ant clean; ant jar > build.log; rm -rf logs/*"
parallel-ssh -i -O StrictHostKeyChecking=no -h $middlewares_host_file $middleware_start_cmd
sleep 8
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

for num_memcached in ${params_num_memcached[@]}
do
	if [ $num_memcached = 2 ]; then
		memcached_connect_string=$(create_vm_ip ${servers[0]})":"$memcached_port" "$(create_vm_ip ${servers[1]})":"$memcached_port
	else
		memcached_connect_string=$(create_vm_ip ${servers[0]})":"$memcached_port" "$(create_vm_ip ${servers[1]})":"$memcached_port" "$(create_vm_ip ${servers[2]})":"$memcached_port
	fi
	for num_middlewares in ${params_num_middlewares[@]}
	do
		if [ $num_middlewares = 1 ]; then
			middlewares=(4)
		else
			middlewares=(4 5)
		fi
		create_pssh_host_files

		for workload in ${params_workload[@]}
		do
			echo "    Starting experiment with" $workload "workload"
			for rep in $(seq $num_repetitions)
			do
				echo "        Starting repetition" $rep
				
				if [ $workload = "writeOnly" ]; then
	               ratio=1:0
	            elif [ $workload = "readOnly" ]; then
					ratio=0:1
				elif [ $workload = "fiftyFifty" ]; then
					ratio=1:1
				fi

				for num_workers in ${params_num_workers_per_mw[@]}
				do
					echo "                Starting experiment with num_workers="$num_workers
					echo $(date +"                Timestamp %H:%M:%S")
					# Start middlewares


				    for mw_id in ${middlewares[@]}
					do
						middleware_cmd="> dstat.log; nohup dstat -cdlmnyt --output dstat.log 1 > /dev/null & cd asl-fall17-project;
				                        nohup java -jar dist/middleware-fchlan.jar -l "$(create_vm_ip $mw_id)" -p "$middleware_port" -t "$num_workers" 
				                        -s false -m "$memcached_connect_string" > /dev/null &"
						ssh $(create_vm_ip $mw_id) $middleware_cmd
					done
					sleep 4
					echo "                Middlewares started"

					
					target_middleware_0_ip=$(create_vm_ip ${middlewares[0]})
					if [ $num_middlewares = 1 ]; then
						target_middleware_1_ip=$(create_vm_ip ${middlewares[0]})
					else
						target_middleware_1_ip=$(create_vm_ip ${middlewares[1]})
					fi

					start_both_memtiers_command="> dstat.log; > ping_0.log;
								echo $(date +%Y%m%d_%H%M%S) > memtier_0.log;
								echo $(date +%Y%m%d_%H%M%S) > ping_0.log;
								nohup dstat -cdlmnyt --output dstat.log 1 > /dev/null &
								ping -Di 1 "$target_middleware_0_ip" -w "$single_experiment_length_sec" > ping_0.log &
								nohup memtier_benchmark -s "$target_middleware_0_ip" -p "$middleware_port" -P memcache_text --key-maximum=10000 --clients="$vc_per_thread" 
								--threads="$num_threads" --test-time="$single_experiment_length_sec" --expiry-range=86400-86401 --data-size=1024 --ratio="$ratio" > memtier_0.log 2>&1 & 
								> ping_1.log;
								echo $(date +%Y%m%d_%H%M%S) > memtier_1.log;
								echo $(date +%Y%m%d_%H%M%S) > ping_1.log;
								ping -Di 1 "$target_middleware_1_ip" -w "$single_experiment_length_sec" > ping_1.log &
								nohup memtier_benchmark -s "$target_middleware_1_ip" -p "$middleware_port" -P memcache_text --key-maximum=10000 --clients="$vc_per_thread" 
								--threads="$num_threads" --test-time="$single_experiment_length_sec" --expiry-range=86400-86401 --data-size=1024 --ratio="$ratio" > memtier_1.log 2>&1 &"
					
					echo "        Starting memtier on clients"
					parallel-ssh -i -O StrictHostKeyChecking=no -h $clients_host_file $start_both_memtiers_command
					
					# Wait for experiment to finish, + 5 sec to account for delays
					sleep $((single_experiment_length_sec + 2))
					
					# Terminate middlewares
					parallel-ssh -i -O StrictHostKeyChecking=no -h $middlewares_host_file pkill --signal=SIGTERM -f java
					sleep 2
					echo "                Middlewares stopped"


		 			# Create folder
		 			log_dir="./"$workload"_"$num_middlewares"mw"$num_memcached"mc"$num_workers"workers/"$rep
		 			mkdir -p $log_dir
		 			cd $log_dir
		 			echo "        Log dir=" $log_dir
					# Copy over logs from memtiers
					parallel-ssh -i -O StrictHostKeyChecking=no -h $clients_host_file pkill -f dstat
					for client_id in ${clients[@]}
					do
						client_vm_ip=$(create_vm_ip $client_id)
						client_dstat_filename=$(create_client_dstat_filename $client_id)
						rsync -r $(echo $nethz"@"$client_vm_ip":~/memtier_0.log") "client_0"$client_id"_0.log"
						rsync -r $(echo $nethz"@"$client_vm_ip":~/memtier_1.log") "client_0"$client_id"_1.log"
						rsync -r $(echo $nethz"@"$client_vm_ip":~/dstat.log") $client_dstat_filename
						rsync -r $(echo $nethz"@"$client_vm_ip":~/ping_0.log") "client_ping_0"$client_id"_0.log"
						rsync -r $(echo $nethz"@"$client_vm_ip":~/ping_1.log") "client_ping_0"$client_id"_1.log"
					done
					parallel-ssh -i -O StrictHostKeyChecking=no -h $clients_host_file rm memtier_0.log memtier_1.log
					# Copy over logs from middlewares. (Should only contain one log folder)
					# Afterwards remove the log folder
					parallel-ssh -i -O StrictHostKeyChecking=no -h $middlewares_host_file pkill -f dstat
					for mw_id in ${middlewares[@]}
					do
						middleware_vm_ip=$(create_vm_ip $mw_id)
						middleware_logs_dirname=$(create_middleware_logs_dirname $mw_id)
						rsync -r $(echo $nethz"@"$middleware_vm_ip":~/asl-fall17-project/logs/*") $middleware_logs_dirname"/"
						rsync -r $(echo $nethz"@"$middleware_vm_ip":~/dstat.log") $middleware_logs_dirname"/dstat.log"
					done
					parallel-ssh -i -O StrictHostKeyChecking=no -h $middlewares_host_file rm -rf ~/asl-fall17-project/logs/*
		 			cd ../..
		 			echo "        ========="
				done
					
			done
		done
	done
done
servers=(6 7 8)
create_pssh_host_files
# Shutdown all memcached servers
parallel-ssh -i -O StrictHostKeyChecking=no -h $servers_host_file pkill -2f memcached; pkill -f dstat
# Copy over logs from memcached
for mc_id in ${servers[@]}
do
	server_vm_ip=$(create_vm_ip $mc_id)
	server_log_filename=$(create_server_log_filename $mc_id)
	server_dstat_filename=$(create_server_dstat_filename $mc_id)
	rsync -r $(echo $nethz"@"$server_vm_ip":~/memcached.log") ~/$folder_name/$server_log_filename
	rsync -r $(echo $nethz"@"$server_vm_ip":~/dstat.log") ~/$folder_name/$server_dstat_filename
done
parallel-ssh -i -O StrictHostKeyChecking=no -h $servers_host_file rm memcached.log

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

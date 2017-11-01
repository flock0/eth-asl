#!/bin/bash

all_vms=(1 2 3 4 5 6 7 8 9)
master=(9)
clients=(1 2 3)
middlewares=(4 5)
servers=(6 7 8)

resource_group=fchlan
vm_nameprefix=foraslvms
vm_dns_suffix=.westeurope.cloudapp.azure.com
nethz=fchlan

master_bootstrap_script_path=./bash/bootstrap_master.sh
client_bootstrap_script_path=./bash/bootstrap_client.sh
middleware_bootstrap_script_path=./bash/bootstrap_middleware.sh
server_bootstrap_script_path=./bash/bootstrap_server.sh
private_ssh_key=~/.ssh/azurevms
public_ssh_key=~/.ssh/azurevms.pub

create_dns_name() {
	echo $nethz"@"$nethz$vm_nameprefix$1$vm_dns_suffix
}

start_vms() {

	for server_id in $1
	do
		echo "Starting VM" $server_id 
		az vm start --name $vm_nameprefix$server_id --no-wait
	done

	echo "Waiting for VMs to start"

	for server_id in $1
	do
		az vm wait --name $vm_nameprefix$server_id --updated
		echo "VM" $server_id "started"
	done
}

bootstrap_master() {
	for server_id in ${master[@]}
	do
		echo "Bootstrapping master" $server_id
		server_name=$(create_dns_name $server_id)
		rsync -r $master_bootstrap_script_path $(echo $server_name":~")
		rsync -r $private_ssh_key $(echo $server_name":~/.ssh/id_rsa")
		rsync -r $public_ssh_key $(echo $server_name":~/.ssh/id_rsa.pub")
		rsync -r bash/sshd_config $(echo $server_name":~/.ssh/config")
		ssh $server_name "nohup bash ./bootstrap_master.sh > ~/bootstrap.log 2>&1 &"
	done
}

bootstrap_clients() {
	for server_id in ${clients[@]}
	do
		echo "Bootstrapping client" $server_id
		server_name=$(create_dns_name $server_id)
		rsync -r $client_bootstrap_script_path $(echo $server_name":~")
		rsync -r $public_ssh_key $(echo $server_name":~/.ssh/id_rsa.pub")
		rsync -r bash/sshd_config $(echo $server_name":~/.ssh/config")
		ssh $server_name "nohup bash ./bootstrap_client.sh > ~/bootstrap.log 2>&1 &"
	done
}

bootstrap_middlewares() {
	for server_id in ${middlewares[@]}
	do
		echo "Bootstrapping middleware" $server_id
		server_name=$(create_dns_name $server_id)
		rsync -r $middleware_bootstrap_script_path $(echo $server_name":~")
		rsync -r $public_ssh_key $(echo $server_name":~/.ssh/id_rsa.pub")
		rsync -r bash/sshd_config $(echo $server_name":~/.ssh/config")
		ssh $server_name "nohup bash ./bootstrap_middleware.sh > ~/bootstrap.log 2>&1 &"
	done
}

bootstrap_servers() {
	for server_id in ${servers[@]}
	do
		echo "Bootstrapping servers" $server_id
		server_name=$(create_dns_name $server_id)
		rsync -r $server_bootstrap_script_path $(echo $server_name":~")
		rsync -r $public_ssh_key $(echo $server_name":~/.ssh/id_rsa.pub")
		rsync -r bash/sshd_config $(echo $server_name":~/.ssh/config")
		ssh $server_name "nohup bash ./bootstrap_server.sh > ~/bootstrap.log 2>&1 &"
	done
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


stop_all_vms() {

	for server_id in ${all_vms[@]}
	do
		echo "Stopping VM" $server_id 
		az vm deallocate --name $vm_nameprefix$server_id --no-wait
	done
}

bootstrap_all_vms() {

	bootstrap_master
	bootstrap_clients
	bootstrap_middlewares
	bootstrap_servers
	
}

az configure --defaults group=$resource_group
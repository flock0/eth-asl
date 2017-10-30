#!/bin/bash

all_vms=(1 2 3 4 5 6 7 8)
clients=(1 2 3)
middlewares=(4 5)
servers=(6 7 8)

resource_group=fchlan
vm_nameprefix=foraslvms
vm_dns_suffix=.westeurope.cloudapp.azure.com

client_bootstrap_script_path=./bash/bootstrap_clients.sh
middleware_bootstrap_script_path=./bash/bootstrap_middlewares.sh
server_bootstrap_script_path=./bash/bootstrap_servers.sh

create_dns_name() {
	echo $nethz"@"$nethz$vm_nameprefix$0$vm_dns_suffix
}

start_vms() {

	for server_id in $1
	do
		echo "Starting VM" $server_id 
		az vm start --name $vm_nameprefix$server_id --no-wait
	done

	for server_id in $1
	do
		az vm wait --name $vm_nameprefix$server_id --updated
		echo "VM" $server_id "started"
	done
}

bootstrap_clients() {
	for server_id in $1
	do
		echo "Bootstrapping client" $server_id
		server_name = create_dns_name server_id
		scp $client_bootstrap_script_path $(echo $server_name":~")
		scp $private_ssh_key $(echo $server_name":/home/.ssh/id_rsa")
		ssh server_name -o "StrictHostKeyChecking no" "bash ./bootstrap_client.sh > "
	done
}

bootstrap_middlewares() {
	for server_id in $1
	do
		echo "Bootstrapping middleware" $server_id
		server_name = create_dns_name server_id
		scp $middleware_bootstrap_script_path $(echo $server_name":~")
		scp $private_ssh_key $(echo $server_name":/home/.ssh/id_rsa")
		ssh server_name -o "StrictHostKeyChecking no" "bash ./bootstrap_middleware.sh"
	done
}

bootstrap_servers() {
	for server_id in $1
	do
		echo "Bootstrapping servers" $server_id
		server_name = create_dns_name server_id
		scp $server_bootstrap_script_path $(echo $server_name":~")
		scp $private_ssh_key $(echo $server_name":/home/.ssh/id_rsa")
		ssh server_name -o "StrictHostKeyChecking no" "bash ./bootstrap_server.sh > "
	done
}

stop_all_vms() {

	for server_id in ${all_vms[@]}
	do
		echo "Stopping VM" $server_id 
		az vm stop --name $vm_nameprefix$server_id --no-wait
	done
}

bootstrap_all_vms() {
	start_vms ${all_vms[@]}

	bootstrap_clients ${clients[@]}
	bootstrap_middlewares ${middlewares[@]}
	bootstrap_servers ${servers[@]}
	
}
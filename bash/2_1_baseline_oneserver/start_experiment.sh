#!/bin/bash

source ../functions.sh

master=9

resource_group=fchlan
vm_nameprefix=foraslvms
vm_dns_suffix=.westeurope.cloudapp.azure.com
nethz=fchlan

master_script_path=./experiment_master_script.sh

if [ ! "$#" == 1 ] || [ ! $1 == "nostart" ]; then
	echo "Starting master" $master 
		az vm start --name $vm_nameprefix$master
	# Wait some time for all VMs to boot
	echo "Waiting for VMs to boot up"
	sleep 120
fi

echo "Using VM" $master "as master"
server_name=$(create_dns_name $master)

rsync -r $master_script_path $(echo $server_name":~")
ssh $server_name "nohup bash ./experiment_master_script.sh > ~/experiment.log 2>&1 &"

echo "Kicked off experiment..."
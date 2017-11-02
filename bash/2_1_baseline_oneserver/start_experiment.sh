#!/bin/bash

source ../functions.sh

all_vms=(1 2 3 7 9)
master=9
clients=(1 2 3)
middlewares=()
servers=(7)

resource_group=fchlan
vm_nameprefix=foraslvms
vm_dns_suffix=.westeurope.cloudapp.azure.com
nethz=fchlan

master_script_path=./experiment_master_script.sh


if [ ! $1 == nostart ]; then
	start_all_vms
fi

echo "Using VM" $master "as master"
server_name=$(create_dns_name $master)

rsync -r $master_script_path $(echo $server_name":~")
ssh $server_name "nohup bash ./experiment_master_script.sh > ~/experiment.log 2>&1 &"

echo "Kicked off experiment..."
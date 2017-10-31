#!/bin/bash

source ../functions.sh

all_vms=(1 2 3 7)
clients=(1 2 3)
middlewares=()
servers=(7)

resource_group=fchlan
vm_nameprefix=foraslvms
vm_dns_suffix=.westeurope.cloudapp.azure.com
nethz=fchlan

master_script_path=./experiment_master_script.sh


start_all_vms
bootstrap_all_vms

echo "Using VM" $all_vms "as master"
server_name=$(create_dns_name $all_vms)

rsync -r $master_script_path $(echo $server_name":~")
ssh $server_name "nohup bash ./experiment_master_script.sh > ~/experiment.log 2>&1 &"

echo "Kicked off experiment..."
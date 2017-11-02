#!/bin/bash

source ../functions.sh

all_vms=(1 6 9)
master=9
clients=(1)
middlewares=()
servers=(6)

resource_group=fchlan
vm_nameprefix=foraslvms
vm_dns_suffix=.westeurope.cloudapp.azure.com
nethz=fchlan

master_script_path=./experiment_master_script.sh
private_ips_path=./private_ips.txt
if [ ! "$#" == 1 ] || [ ! $1 == "nostart" ]; then
	start_all_vms
	# Wait some time for all VMs to boot
	echo "Waiting for VMs to boot up"
	sleep 120
fi
# Fetch private IPs
echo "Fetching private IPs"
> $private_ips_path
for vm_id in ${all_vms[@]}
do
	az vm show --name $vm_nameprefix$vm_id --query privateIps -d --out tsv >> $private_ips_path
done


echo "Using VM" $master "as master"
server_name=$(create_dns_name $master)

rsync -r $master_script_path $(echo $server_name":~")
rsync -r $private_ips_path $(echo $server_name":~")
ssh $server_name "nohup bash ./experiment_master_script.sh > ~/experiment.log 2>&1 &"

echo "Kicked off experiment..."
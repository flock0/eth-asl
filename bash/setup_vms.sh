#!/bin/bash

source functions.sh
start_all_vms
echo "Sleeping for 120 sec to let the VMs boot up"
sleep 120
bootstrap_all_vms
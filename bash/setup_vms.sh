#!/bin/bash

source functions.sh
az configure --defaults group=$resource_group
bootstrap_all_vms
#!/bin/bash

cat ~/.ssh/id_rsa.pub >> ~/.ssh/authorized_keys
echo "deb [arch=amd64] https://packages.microsoft.com/repos/azure-cli/ wheezy main" | \
     sudo tee /etc/apt/sources.list.d/azure-cli.list
sudo apt-key adv --keyserver packages.microsoft.com --recv-keys 52E16F86FEE04B979B07E28DB02C46DF417A0893
sudo apt-get install apt-transport-https -y
sudo apt-get update -y
sudo apt-get install git unzip azure-cli -y
if [ -d "./ethz-asl-experiments" ]; then
	cd ethz-asl-experiments/
	git pull
	cd
else
	git clone git@gitlab.ethz.ch:fchlan/ethz-asl-experiments.git
fi
git config --global push.default matching

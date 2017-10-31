#!/bin/bash

cat ~/.ssh/id_rsa.pub >> ~/.ssh/authorized_keys
sudo apt-get update -y
sudo apt-get install git unzip ant openjdk-8-jdk -y
if [ -d "./ethz-asl-experiments" ]; then
	cd ethz-asl-experiments/
	git pull
	cd
else
	git clone git@gitlab.ethz.ch:fchlan/ethz-asl-experiments.git
fi
git clone git@gitlab.ethz.ch:fchlan/asl-fall17-project.git
git checkout develop
cd asl-fall17-project/
git checkout develop
ant jar
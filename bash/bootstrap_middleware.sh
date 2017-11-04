#!/bin/bash

cat ~/.ssh/id_rsa.pub >> ~/.ssh/authorized_keys
sudo apt-get update -y
sudo apt-get install git unzip ant openjdk-8-jdk dstat -y
git clone git@gitlab.ethz.ch:fchlan/asl-fall17-project.git
git checkout develop
cd asl-fall17-project/
git checkout develop
ant jar
#!/bin/bash

sudo apt-get update
sudo apt-get install git unzip ant openjdk-8-jdk
git clone git@gitlab.ethz.ch:fchlan/asl-fall17-project.git
git checkout develop
cd asl-fall17-project/
git checkout develop
ant jar
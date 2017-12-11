#!/bin/bash

cat ~/.ssh/id_rsa.pub >> ~/.ssh/authorized_keys
sleep 10
sudo apt-get update -y
sudo apt-get install memcached unzip openjdk-8-jdk dstat -y
sudo service memcached stop
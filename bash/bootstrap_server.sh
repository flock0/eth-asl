#!/bin/bash

cat ~/.ssh/id_rsa.pub >> ~/.ssh/authorized_keys
sudo apt-get update -y
sudo apt-get install memcached unzip openjdk-8-jdk -y
sudo service memcached stop
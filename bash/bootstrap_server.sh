#!/bin/bash

sudo apt-get update -y
sudo apt-get install memcached unzip openjdk-8-jdk -y
sudo service memcached stop
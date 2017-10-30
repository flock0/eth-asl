#!/bin/bash

sudo apt-get update
sudo apt-get install memcached unzip openjdk-8-jdk
sudo service memcached stop
memcached -t 1 -p 11211
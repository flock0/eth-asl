#!/bin/bash

sudo apt-get update
sudo apt-get install git unzip build-essential autoconf automake libpcre3-dev libevent-dev pkg-config zlib1g-dev
wget https://github.com/RedisLabs/memtier_benchmark/archive/master.zip
unzip master.zip
cd memtier_benchmark-master
sudo apt-get install 
autoreconf -ivf
./configure
make
sudo make install
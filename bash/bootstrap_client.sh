#!/bin/bash

cat ~/.ssh/id_rsa.pub >> ~/.ssh/authorized_keys
sudo apt-get update -y
sudo apt-get install unzip build-essential autoconf automake libpcre3-dev libevent-dev pkg-config zlib1g-dev -y
wget -q https://github.com/RedisLabs/memtier_benchmark/archive/master.zip -O ~/master.zip
unzip -o master.zip
cd memtier_benchmark-master
autoreconf -ivf
./configure
make
sudo make install
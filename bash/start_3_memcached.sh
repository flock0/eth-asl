#!/bin/bash

memcached -m 64 -c 1024 -u memcached -p 11211 -vvv & memcached -m 64 -c 1024 -u memcached -p 11212 -vvv & memcached -m 64 -c 1024 -u memcached -p 11213 -vvv

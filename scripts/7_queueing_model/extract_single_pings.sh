#!/bin/bash

grep 'bytes from' $1 | awk '{print $1,$6,$8}' >> $2
#!/bin/bash

cd $1
sed 's/\r/\n/g' client_01_0.log | grep '\[RUN #1 ' | awk '{print "1", $4, $10, $17}' >> client_01_0.log.extracted
sed 's/\r/\n/g' client_01_1.log | grep '\[RUN #1 ' | awk '{print "2", $4, $10, $17}' >> client_01_1.log.extracted
#!/bin/bash

cd $1
sed 's/\r/\n/g' client_01.log | grep '\[RUN #1 ' | awk '{print "1", $4, $10, $17}' >> client_01.log.extracted
sed 's/\r/\n/g' client_02.log | grep '\[RUN #1 ' | awk '{print "2", $4, $10, $17}' >> client_02.log.extracted
sed 's/\r/\n/g' client_03.log | grep '\[RUN #1 ' | awk '{print "3", $4, $10, $17}' >> client_03.log.extracted
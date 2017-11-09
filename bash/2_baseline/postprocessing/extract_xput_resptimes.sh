#!/bin/bash

cd $1
echo $2 $3
sed 's/\r/\n/g' client_01.log | grep '\[RUN #1 ' | awk '{print "1", $4, $10, $17}' > client_01.log.extracted
sed 's/\r/\n/g' client_02.log | grep '\[RUN #1 ' | awk '{print "2", $4, $10, $17}' > client_02.log.extracted
sed 's/\r/\n/g' client_03.log | grep '\[RUN #1 ' | awk '{print "3", $4, $10, $17}' > client_03.log.extracted

join -1 2 -2 2 client_01.log.extracted client_02.log.extracted > 1_2_joined.txt; join -1 1 -2 2 1_2_joined.txt client_03.log.extracted | awk -v min=$2 -v max=$3 '{if ($1 >= min && $1 <= max) print $1, $3+$6+$9, ($4+$7+$10)/3}' > clients.aggregated
cat client_01.log.extracted client_02.log.extracted client_03.log.extracted | awk -v min=$2 -v max=$3 '{if ($2 >= min && $2 <= max) print $0}' > clients.concatenated

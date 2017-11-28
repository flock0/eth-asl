#!/bin/bash

sed 's/\r/\n/g' $1 | grep '\[RUN #1 ' | awk '{print $4, $10, $17}' >> $2
#!/bin/bash
size=$(stat -c%s $1)
if [ `expr $size % 2` -eq 1 ]
then
    truncate -s +1 $1
fi

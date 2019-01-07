#!/usr/bin/env bash
arr=(1 2 3)
arr[10]="yutao"
for index in ${arr[@]};do
    echo $index
done
echo ${arr[9]}
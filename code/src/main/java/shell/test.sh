#!/usr/bin/env bash
while :
do
    echo -n "输入1到5之间的数字："
    read aNum
    case ${aNum} in
        1) echo "echo ${aNum}"
        ;;
        *) echo "你输入的不是"
        break
        ;;
    esac
done

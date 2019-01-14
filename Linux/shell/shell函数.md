#定义函数
```bash
:<<EOF
[function] name [()]
{
函数体
[return int;]
}
EOF

```
1. 可以带function fun()定义，也可以直接fun()定义，不带任何参数
2. 参数返回，可以显示加：return返回，如果不加，将以最后一条命令运行结果，作为返回值。return后跟数值n（0-255）只能是数值，不能是字符串

```bash
demofun(){
    echo "hello, world"
}

echo "函数执行开始"
demofun
echo "函数执行完毕"

```

#调用函数
直接调用函数名称就行，不用加括号

#函数参数
函数参数和命令行参数是一样的套路![函数参数参照命令行参数](命令行参数.md)
[javaDNS查询内部实现](https://www.cnblogs.com/crazyacking/p/5672032.html)

可以看到这个类基本没有实现什么逻辑，主要实现都是通过JNI调用native方法了。native方法其实就是系统调用了。这里就不展开了，逻辑不外乎就是查看/etc/resolv.conf下配置的nameserver和/etc/hosts下面的配置，然后使用DNS协议查询。

[anylocalNameServer](https://my.oschina.net/xiaominmin/blog/1598818)
[ping主机名或者域名的过程](https://www.cnblogs.com/u013533289/p/11629045.html)

最新的没有装蓝灯的备份 12-14，5点47分
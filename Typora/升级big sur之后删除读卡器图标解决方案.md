# 升级big sur之后删除读卡器图标解决方案

* 关闭apple的sip机制 参考 https://blog.csdn.net/Carina_Cao/article/details/115076964，只需要关闭sip就行了
    * csrutil disable
    * csrutil authenticated-root disable
* 将系统文件挂载到一个可读的目录下
    * cd && mkdir mnt && sudo mount -o nobrowse -t apfs /dev/disk2s2 /Users/yutao/mnt
    * cd mnt/System/Library/CoreServices/Menu\ Extras
    * sudo mv ExpressCard.menu ExpressCard.menu.back &&sudo touch ExpressCard.menu
* 需要将修改的系统文件写入到snapshot中 参考 https://www.zhihu.com/question/403361335
    * sudo bless --folder /Users/yutao/mnt/System/Library/CoreServices --bootefi --create-snapshot
* 去recovery模式开启sip
    * csrutil enable
    * csrutil authenticated-root enable


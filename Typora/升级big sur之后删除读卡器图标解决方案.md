# 升级mac os之后删除读卡器图标解决方案

* 关闭apple的sip机制 参考 https://blog.csdn.net/Carina_Cao/article/details/115076964，只需要关闭sip就行了
    * csrutil disable
    * csrutil authenticated-root disable
* 将系统文件挂载到一个可读的目录下
    * cd && mkdir mnt && sudo mount -o nobrowse -t apfs /dev/disk2s2 /Users/yutao/mnt
    * 如果不行尝试disk2s1(如果没有清空过磁盘应该默认是disk2s1，具体是哪个磁盘可以在磁盘工具-现实所有设备 可以看到系统data是哪个盘符)
    * cd && mkdir mnt && sudo mount -o nobrowse -t apfs /dev/disk2s1 /Users/yutao/mnt
    * 如果遇到报错可以使用diskutil fore unmount
    * 如遇到 “mount_apfs: volume could not be mounted: Resource busy”问题，try this: “diskutil unmountDisk force /dev/disk#” ( whatever your disk# is)
    * 将系统盘mount好之后需要干两件事，一是删除读卡器图标，二是更换壁纸，壁纸在iCloud里面存着，直接拉下来就好
    * 1. 首先删除读卡器图标
    * cd mnt/System/Library/CoreServices/Menu\ Extras
    * sudo mv ExpressCard.menu ExpressCard.menu.back && sudo touch ExpressCard.menu
    * 2. 更换壁纸
    * cd /Users/yutao/mnt/System/Library/Desktop Pictures
    * 这个时候因为已经挂载好了，所以已经有读写权限了直接使用ofd命令打开对应的finder文件夹，然后把所有desktop结尾的文件删除
    * 再将所有mac壁纸.zip解压出来的数据放到文件夹里面
* 需要将修改的系统文件写入到snapshot中 参考 https://www.zhihu.com/question/403361335
    * sudo bless --folder /Users/yutao/mnt/System/Library/CoreServices --bootefi --create-snapshot
* 去recovery模式开启sip
    * csrutil enable
    * csrutil authenticated-root enable

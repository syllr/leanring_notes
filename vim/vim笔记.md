# 在vim中使用jq
在vim的ex模式中%代表vim正在编辑的文本的所有内容，在vim的ex模式中直接使用`:% !command`其实就相当于通过一个管道将vim正在编辑的文件内容通过标准输入传入到相对应的命令中，然后vim会自动将shell命令的输出重新写入到文件中（是重写overwrite，也就是将现有的文件内容全部删除，然后将新的内容写进去），所以利用该特点可以在vim中调用jq然后将对json文件做处理`% !jq [.SegmentList[].SegmentDetailList[].FlightInfoList[].CabinFareList[].ProductPriceList[].PriceList[]]`

# vim中执行shell命令的小结

## 在vim中执行shell命令的几种方式

### :!command
* 使用!来执行shell命令，这样写双击tab不会有提示
* 可以运行:!bash来启动一个bash shell并执行命令，不需要退出vim（这种办法会在归中有乱码问题，不要用）
* 可以使用:terminal直接开一个终端来执行shell命令
### 执行shell命令的一些选项
* :r !command 会将命令的结果插入到文件的当前行的下一行
* % !command 会将全部文本通过管道传到命令中，并且将命令的结果重写掉文件的内容
* 起始行号,结束行号 !command 和上面一样，只不过选择了一定的区域
* 起始行号，结束行号（或则%）w !command 会将选中的区域的文本当作命令执行并显示结果，不会改变当前编辑的文件的内容

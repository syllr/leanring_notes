# linux中常见的压缩格式
linux中常见的压缩格式有5种
* .zip
* .gz
* .bz2
* .tar.gz
* .tar.bz2

# .zip格式
## Create zip file from a file
zip archive.zip file1 

## Create zip file from a directory
zip -r archive.zip directory/

> 如果直接用zip archive.zip directory/这个命令不加r 参数的话也不会报错，但是打出来的archive.zip压缩包里面是空的，没有文件，只有加了-r命令才会将文件夹里面的东西放进压缩包里面

## Extract archive
unzip archive.zip

> 值得注意的是zip格式的解压是不分目录和文件的，所以没有-r的选项
          
## Test integrity of archive
unzip -tq archive.zip

## List files and directories in a file
unzip -l archive.zip

# .gz格式
## To create a *.gz compressed file
gzip test.txt

## To create a *.gz compressed file to a specific location using -c option (standard out)
gzip -c test.txt > test_custom.txt.gz

## To uncompress a *.gz file
gzip -d test.txt.gz

gunzip test.txt.gz

## Display compression ratio of the compressed file using gzip -l
gzip -l *.gz

## Recursively compress all the files under a specified directory
gzip -r documents_directory

## To create a *.gz compressed file and keep the original
gzip < test.txt > test.txt.gz

# .bz2格式
## compress foo -> foo.bz2
bzip2 -z foo

## decompress foo.bz2 -> foo
bzip2 -d foo.bz2

## compress foo to stdout
bzip2 -zc foo > foo.bz2

## decompress foo.bz2 to stdout
bzip2 -dc foo.bz2

> 注意一下gzip命令是可以压缩和解压目录的，但是gzip的压缩和解压目录和zip不同，gzip是将改目录的所有文件分别压缩或则解压，所以本质上其实相当于没有压缩或则解压目录，而bzip2不支持文件夹作为参数

# tar的自动识别功能
现代的tar已经有了自动识别的功能，能够根据打包格式的后缀名自动的调用相关的程序执行打包压缩，或则是解压的功能（其实是通过管道）
## 自动压缩
tar -cavf xxx.tar.gz | tar -cavf xxx.tar.bz2 xxx/

* c指的是压缩
* a指的是自动检测
* v指的是在控制台输出相关信息
* f指的是输出信息的基本单位为文件

## 自动解压
tar -xavf xxx.tar.gz | tar -xavf xxx.tar.bz2

* x指的是解压
* a指的是自动检测
* v指的是在控制台输出相关信息
* f指的是输出的信息的基本单位为文件

# 各种命令

## sort

sort [-bcfMnrtk][源文件][-o 输出文件]
补充说明：sort可针对文本文件的内容，以行为单位来排序。
参数：
  -b   忽略每行前面开始出的空格字符。
  -c   检查文件是否已经按照顺序排序。
  -f   排序时，忽略大小写字母。
  -r   以相反的顺序来排序。
  -t<分隔字符>   指定排序时所用的栏位分隔字符。
  -M   将前面3个字母依照月份的缩写进行排序。
  -n   依照数值的大小排序。
  -o<输出文件>   将排序后的结果存入指定的文件。
  -r   以相反的顺序来排序。
  -t<分隔字符>   指定排序时所用的栏位分隔字符。
  -k  选择以哪个区间进行排序。-ka,b 表示[a,b]区间

文件按时间排序
ls -ltr

### 高级示例

按多字段排序。

sort temp -k1,1 -n -k2,2 排序temp文件，先按第一列字符串排序，在按第二列数字排序。

## rsync

rsync -rzcv --delete --chmod='a=rX,u+w' --rsync-path='sudo rsync' 项目目录/* 机器名:web应用目录/webapps/ROOT --exclude=.svn --exclude=.git --temp-dir=/tmp
# hive

## 本地搭建

详情参照网上教程，主要就是下载 + 改xml配置。需要注意的是，改xml配置时，需要配置metadata所在的db，我用的是mysql，注意将xml中原配置替换。
之前遇到过问题就是默认配置没有删除干净，导致一些环境仍然用的是derby。

### metadata配置

## 操作

### 表和hdfs

外部表

```sql
create external table test2 (
    id      int,
    name    string,
    hobby   array<string>,
    add     map<String,string>
)
row format delimited
fields terminated by '\t'
collection items terminated by '-'
map keys terminated by ':'
location '/user/xiaoshuang.cui/hive_data/second_test';
```
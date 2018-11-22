# hive

## 本地搭建

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
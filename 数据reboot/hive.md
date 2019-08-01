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

## hiveserver2

- [ ] 没有征服。启动后，显示hadoop的userproxy相关错误，按照网上教程，修改了core-site，也没有用，是因为我用户名中间有点号原因？

上面的问题尝试解决了下。之前启动时是直接 ./hiveserver2，这样是以xiaoshuang.cui的身份启动，xml中的配置无法生效，导致在beeline上用任何账号登陆都失败。
***我现在基本确定了是用户名有点号，导致xml配置失效。因为我用sudo -uroot ./hiveserver2 启动后，下面的配置能生效***

```xml
<property>
  <name>hadoop.proxyuser.root.hosts</name>
  <value>*</value>
</property>
<property>
  <name>hadoop.proxyuser.root.groups</name>
  <value>*</value>
</property>
```

### 用户代理机制

### SQL

#### 注意事项

- 使用split将数据切分成array时，如果数据是空字符串，结果是包含一个空字符串的array，而不是空array

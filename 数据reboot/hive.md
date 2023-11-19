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

### 窗口函数

//TODO

首先，窗口计算需要有一些基本的函数。比如简单的统计 SUM, MAX, MIN, AVG等。还有一些序列函数 NTILE,ROW_NUMBER,RANK,DENSE_RANK等。一些分析函数 LAG,LEAD,FIRST_VALUE,LAST_VALUE等。

还有一些其他的函数，比如 GROUPING SETS,GROUPING__ID,CUBE,ROLLUP，不涉及到 window子句，暂时不说了。

在SQL处理中，窗口函数都是最后一步执行，而且仅位于Order by字句之前。

#### over子句

over是开窗函数，给每条记录打开一个数据集。数据集应该包含哪些数据，由over子句中方法定义。

window子句。几个关键词如下：

- ROWS : 排序后按物理行号取数据。
- RNAGE: 排序后按逻辑大小取数据。
- PRECEDING : 往前
- FOLLOWING : 往后
- CURRENT ROW : 当前行
- UNBOUNDED : 无限制

over () 默认是 rows between UNBOUNDED PRECEDING and UNBOUNDED FOLLOWING，表示满足窗口函数之前条件的所有行。

rows和range区别在于，例如 order by price rows/range 10 PRECEDING and 10 FOLLOWING，
那么rows取的是排序后的当前行的前10行和后10行，range取的是排序后当前id的 [id-10, id+10] 范围内的数据，

order by子句。表示窗口内数据按某维度排序。

partition by子句。表示窗口内数据，按某维度聚合。每一行的默认的开窗范围为当前行所在分组的所有记录。

#### 序列函数

NTILE : 将数据分成若干组，在每条数据后添加一个组号。数据不均匀时，在将多的数据放在第一组中。

ROW_NUMBER : 生成从1开始的行号

RANK : 生成数据项在分组中的排名，排名相等会在名次中留下空位

DENSE_RANK : 生成数据项在分组中的排名，排名相等会在名次中不会留下空位

#### 分析函数

LAG : 统计窗口中当前行前n行（不包含当前行）

LEAD : 统计窗口中当前行后n行（不包含当前行）

FIRST_VALUE : 统计窗口中截止当前行的第一个条记录

LAST_VALUE : 统计窗口中截止当前行的最后一个条记录。如果要取窗口内排序后最后一条记录，可以用desc排序，再用 FIRST_VALUE。

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

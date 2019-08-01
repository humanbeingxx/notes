# hbase

## 数据模型

[Introduction to HBase Schema Design](http://0b4af6cdc2f0c5998459-c0245c5c937c5dedcca3f1764ecc9b2f.r43.cf2.rackcdn.com/9353-login1210_khurana.pdf)

### Row

rowkey唯一标识了一条数据，没有数据类型，总是当做byte[]处理。

### Column

#### Column Family, Column Qualifier

Column Family是多个列的集合，Column Qualifier则代表了某个列。
表示格式是 Column Family:Column Qualifier

Family还保存了一系列属性，比如是否会在内存中缓存、压缩方式、rowkey如何编码等。

*Column Family是建表时确定好的，但是一个Family包含的Qualifier是可以动态添加的。*

### Cell

cell = rowkey + cf + cq

### timestamp

每个cell都有一个版本号，不指定的话就是写入时的timestamp。

### 帮助理解

下面hbase的数据

| rowkey | CF1:CQ1    | CF1:CQ2    | CF2:CQ1    | CF2:CQ2    |
| ------ | ---------- | ---------- | ---------- | ---------- |
| 0001   | value1_1_1 | value1_1_2 | value1_2_1 | value1_2_2 |
| 0002   | value2_1_1 | value2_1_2 | value2_2_1 | value2_2_2 |
| 0003   | value3_1_1 | value3_1_2 | value3_2_1 | value3_2_2 |

可以看做是一个多维map，例如0001这条数据

```json
{
    "0001":
        {
            "CF1":
                {
                    "CQ1":
                        {
                            "timestamp1":"value1_1_1.old",
                            "timestamp2":"value1_1_1"
                        },
                    "CQ2":
                        {
                            "timestamp1":"value1_1_2"
                        }
                },
            "CF2":
                {
                    "CQ1":
                        {
                            "timestamp1":"value1_2_1"
                        },
                    "CQ2":
                        {
                            "timestamp1":"value1_2_2"
                        }
                }
        }
}
```

关于默认版本号：

1. 根据rowkey读取时，如果不指定版本号，默认取最新的一条。
2. 读取某个CF，如果不指定版本号，默认取最新的一条。
3. 读取某个CF:CQ，如果不指定版本号，会返回所有版本。

## rowkey的设计

rowkey是唯一可能用成索引的地方。如果不设计好rowkey，条件查询只能全表扫描在过滤了。

rowkey是byte[]，按照字典顺序排序。在rowkey上建索引，其实也就是对rowkey做一次数据冗余。

## LSM(The Log-Structured Merge-Tree 日志结构合并树)

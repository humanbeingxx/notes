# in vs exists

一般的说法，in适合内层表比较小，外层表比较大的场景；exists适合内层表比较大，外层表比较小的场景。

- in先执行内部子查询，再遍历外层，判断是否满足内层条件。
- exists先遍历外部数据，再对每条数据遍历内层数据，判断是否满足条件。

针对in，有个疑问，内外层大小有什么影响，判断次数不都是“外层数量*内层数量”吗？

做了实验，相同的两张表，调换两张表的内外顺序后，执行速度确实有差距。这里我觉得是进行内层判断时进行了优化。

*对多个字段用in子查询时，可以用(a,b,c)的方式同时查出*

```sql
select
    count(*)
from
    table1
where
    (key1, key2, key3) in (
        select
            key1,
            key2,
            key3
        from
            table2
    )
```

## 当内外表大小差不多

对比三种方式，in、exists、join。

我用了8w条数据测试，发现效率 in >>> join > exists

exists效率低，是因为发生了DEPENDENT SUBQUERY。说明子查询的select依赖外部的查询结果。结果就是需要执行8w次子查询。

join效率低，可能是因为我执行的case原因，join所用的key区分度不高，导致结果乘积量很大。

**mysql中，可能会将in子查询改成exists，这个是优化比较差的一点。**
但是这个case中，mysql的表现还不错，执行计划如下。符合先子查询再外层查询的预期执行计划。

```plain

*************************** 1. row ***************************
           id: 1
  select_type: SIMPLE
        table: fact_village_scale_portrait
   partitions: NULL
         type: index
possible_keys: NULL
          key: main_id
      key_len: 27
          ref: NULL
         rows: 81152
     filtered: 100.00
        Extra: Using where; Using index
*************************** 2. row ***************************
           id: 1
  select_type: SIMPLE
        table: <subquery2>
   partitions: NULL
         type: eq_ref
possible_keys: <auto_key>
          key: <auto_key>
      key_len: 4
          ref: szc.fact_village_scale_portrait.village_id
         rows: 1
     filtered: 100.00
        Extra: NULL
*************************** 3. row ***************************
           id: 2
  select_type: MATERIALIZED
        table: fact_village_scale_portrait_shell
   partitions: NULL
         type: index
possible_keys: NULL
          key: main_id
      key_len: 27
          ref: NULL
         rows: 82175
     filtered: 100.00
        Extra: Using index

```

一种优化方案是将子查询改成join，如下sql。但是这种方式，对于table2中查出的条数比较少时好用，但是条数多时，还是会由于join量太大，效率低下。

```sql
select
    count(*)
from
    table1,
    (
        select
            key
        from
            table2
    ) t2
where
    table1.key = t2.key
```

## 我的实验结果

按照网上说法：一般的说法，in适合内层表比较小，外层表比较大的场景；exists适合内层表比较大，外层表比较小的场景。

我做了如下实验：

表A有1600条数据，表B有82000条数据。每个表有10个字段左右，并且两张表都有字段`field_a`, `field_b`，且`field_a`为索引。

先用索引字段做查询，有如下sql:

```sql
select count(*) from A where exists (select field_a from B where A.field_a = B.field_a);

select count(*) from B where exists (select field_a from A where A.field_a = B.field_a);

select count(*) from A where field_a in (select field_a from B);

select count(*) from B where field_a in (select field_a from A);
```

实验结果如下：

1. 第一条sql，执行消耗0.01s。
2. 第二条sql，执行消耗0.39s。
3. 第三条sql，执行消耗0.05s。
4. 第四条sql，执行消耗0.01s。

**可以看出，对于exists，小表在外运行更快。对于in，大表在外运行更快。**

再用非索引字段做查询，有如下sql:

```sql
select count(*) from A where exists (select field_b from B where A.field_b = B.field_b);

select count(*) from B where exists (select field_b from A where A.field_b = B.field_b);

select count(*) from A where field_b in (select field_b from B);

select count(*) from B where field_b in (select field_b from A);
```

实验结果如下：

1. 第一条sql，执行消耗23s。
2. 第二条sql，未执行出来（5min内）。
3. 第三条sql，执行消耗0.1s。
4. 第四条sql，执行消耗8.8s。

**可以看出，对于exists，小表在外运行更快。对于in，也是小表在外运行更快。**

***总结：对于exists，总是小表在外速度更快。对于in，当用到索引时，大表在外更快，因为外层是索引查询，内层是遍历；当不用索引时，小表在外运行更快，通过执行计划看出小表在外时，先进行子查询再外层表遍历，大表在外时，先物化内层表再两表做join。***

对于否定形式，not exists和exists表现一致。not in用到索引时，和in表现一致，用不到索引时，和`select count(*) from A where field_b in (select field_b from B);`表现一致，执行计划也是一致的。

针对用不到索引的情况下，执行比较慢的第一、二、四条sql，尝试用join优化。

```sql
select count(*) from A join B on A.field_b = B.field_b;
```

执行消耗5.9s，比其他三条效率高。同时执行计划中仅有一个Using join buffer(Block Nested Loop)。但是这个sql的执行结果和上面几个sql不一致，因为join时会产生乘积关系。

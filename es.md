# es

## 使用

term/terms 查询被用于精确值匹配，这些精确值可能是数字、时间、布尔或者那些 not_analyzed 的字符串。term 查询对于输入的文本不分析 ，所以它将给定的值进行精确查询。

exists 查询和 missing 查询被用于查找那些指定字段中有值 (exists) 或无值 (missing) 的文档。这与SQL中的 IS_NULL (missing) 和 NOT IS_NULL (exists) 在本质上具有共性。

使用explain命令查看文档为什么没有匹配

```json
GET /us/tweet/12/_explain

{
   "query" : {
      "bool" : {
         "filter" : { "term" :  { "user_id" : 2           }},
         "must" :  { "match" : { "tweet" :   "honeymoon" }}
      }
   }
}
```

must和should的关系

> 所有 must 语句必须匹配，所有 must_not 语句都必须不匹配，但有多少 should 语句应该匹配呢？默认情况下，没有 should 语句是必须匹配的，只有一个例外：那就是当没有 must 语句的时候，至少有一个 should 语句必须匹配。

minimum_should_match 当查询中有should且没有filter或者must时，默认值是1，表示至少要满足一个should条件。如果有filter或者must，则表示不需要满足should条件，只要满足must或者filter即可。

### 用boost_query控制得分

具体的算法使TF-IDF，这里说的是如下：

```json
{
  "query": {
    "boosting": {
      "positive": {
        "term": {
          "text": "apple"
        }
      },
      "negative": {
        "term": {
          "text": "pie tart fruit crumble tree"
        }
      },
      "negative_boost": 0.5
    }
  }
}
```

negative表示满足条件的文档减少50%的得分。

#### 查询时的固定分数

如果只有filter，则文档得分都是0。
如果是match_all，则得分都是1。
用constant_score代替bool，则得分都是1。可以通过boost参数控制分数。


### dis_max 查询

```json
GET /_search
{
  "query": {
    "dis_max": {
      "queries": [
        { "term": { "title": "Quick pets" } },
        { "term": { "body": "Quick pets" } }
      ],
      "tie_breaker": 0.3
    }
  }
}
```

当有多个查询条件，如果文档符合一个或多个条件，那么需要一种算分方式。dis_max是`best_fields`方式，即选择匹配term最多的子句作为算分子句。
如果想同时把其他子句也加入算分中，那么使用tie_breaker，则`score = best_fields + tie_breaker * other_match`。
一般tier_breaker取值在0.1-0.4，如果大于0.5，则没有了`best_fields`的语义。

使用multi_match也能达到同样的效果。上面的查询可以改写成。

```json
GET /_search
{
  "query": {
    "multi_match": {
      "query": "Quick pets",
      "type": "best_fields",
      "fields": ["title", "body"],
      "tie_breaker": 0.3
    }
  }
}
```

### 分页

#### scroll处理深分页

创建scroll视图时，先按照搜索条件和排序获取到全部且排好序的文档id，这实际就是query阶段。将这部分id保存到内存，并记录当前游标位置。

 - [x] 是将全量id保存到协调节点，还是每个shard的数据保存在每个shard节点上？

> 看了源码后，是保存在每个shard节点上。协调节点会保存scroll每次fetch数据后每个shard的lastEmittedDocPerShard，并在fetch时发送给shard节点。

**事情并没有这么简单。在官方文档中提到，scroll期间，为了防止当前正在使用的segment被段合并后删除，如果有正在进行的scroll，段合并会暂停。如果按照上面的说法，先获取id，再遍历，为什么会有段被正在用的问题？段合并不会导致数据增加或者减少，也不会导致id变化，为什么需要暂停？在search-after中，也会因为正在进行的PIT，导致段合并暂停，为什么？即使段不合并，也会有数据写入，暂停端合并能解决什么问题？**

#### search-after处理深分页

基本版的search-after并不是很深奥，需要制定一个固定sort，并在下一次search时带上上次结果的最后一条数据的sort信息。类似mysql按id翻页。

要求query和sort保持一致，且默认（隐式）使用 _shard_doc_ + asc 作为tiebreak（当两条数据的排序值一样时）。

由于search-after是在当前数据上进行search，当segment发生变化时，可能造成翻页数据错误，例如数据重复出现或者丢失。为了解决这个问题，引入了`PIT(point in time)`。

___

dis_max查询: 将任何与任一查询匹配的文档作为结果返回，但只将最佳匹配的评分作为查询的评分结果返回。

best_field: 将最佳匹配字段的评分（匹配了最多关键字的字段）作为查询的整体评分。

most_field: 根据全部匹配字段计算评分。一般使用场景是对相关度进行微调，将同一个数据索引到不同字段。例如将单词的词源、同义词、变音词等作为不同字段。

match_phrase: 用于匹配短语，普通的match会将查询语句分词再每一个词匹配，match_phrase可以保持短语的顺序关系。

___

search时，使用collapse参数，可以按照字段取若干排名的数据。同时在inner_hits中可以按照规则排序，获取多个文档。例如下面的查询，外层取每个性别下最高分文档，inner_hits是按年龄排序前2名。

```json
{
    "collapse": {
        "field": "gender",
        "inner_hits": {
            "name": "max_age",
            "size": 2,
            "sort": [{"age":"asc"}]
        }
    }
}
```

**但是innter_hits的实现是对外层的每一个hit和innter_hits的个数，发送额外的query。可能存在性能问题。**

filter用于过滤数据，对search和aggregation都生效，即search返回的结果是filter后的数据，aggregation也是在filter的结果上进行聚合。
post_filter只对search的结果生效，不影响aggregation的数据集。

### join类型的使用

join类型是一个特殊的字段类型，例如创建如下的mapping:

```json
{
  "mappings": {
    "properties": {
      "my_id": {
        "type": "keyword",
      },
      "join_field": {
        "type": "join",
        "relations": {
          "question": ["answer", "comment"],
          "answer": "vote"
        }
      }
    }
  }
}
```

一个question可以对应多个answer和comment，answer又可以对应多个vote。（然而在es中不要使用多级join）

写入父文档

```json
{
  "my_id": 1,
  "name": "question1",
  "join_field": "question"
}
```

写入子文档

```json
{
  "my_id": 2,
  "join_field": {
    "name": "answer",
    "parent": 1
  }
}
```

根据父文档查询子文档(has_parent query)

```json
{
  "query": {
    "has_parent": "question",
    "query": {
      "term": {
        "name": "question1"
      }
    }
  }
}
```

根据子文档查询父文档(has_child query)

```json
{
  "query": {
    "has_child": {
      "type": "answer",
      "query": {
        "match_all": {}
      },
      "max_children": 10,
      "min_children": 2
    }
  }
}
```

min_children和max_children分别表示满足条件的子文档数的最少和最多数量，不满足这个数量条件的会被过滤。

- [ ] 和nested的对比？
- [ ] nested的存储和搜索原理？
- [ ] join的存储和搜索原理？

#### join文档的多层级

es中的join通过`join`类型的结构实现的。

```json
PUT /my-index-000001
{
  "mappings": {
    "properties": {
      "my-join-field": {
        "type": "join",
        "relations": {
          "my-parent": "my-child"
        }
      }
    }
  }
}
```

父文档的`my-join-field`值是`my-parent`，子文档的是`my-child`，同时还需要包含一个父文档的doc_id。

官方文档中提到一个限制：

> It is also possible to add a child to an existing element but only if the element is already a parent.

是不是意味着es中的join文档是分父子类型的。

例如下面的文档。

```json
PUT /my-index-000001/_doc/1?refresh
{
  "text": "This is a parent document.",
  "my-join-field": "my-parent"
}


PUT /my-index-000001/_doc/2?routing=1&refresh
{
  "text": "This is a child document.",
  "my-join-field": {
    "name": "my-child",
    "parent": "1"
  }
}

PUT /my-index-000001/_doc/2?routing=2&refresh
{
  "text": "This is a child-child document.",
  "my-join-field": {
    "name": "my-child",
    "parent": "2"
  }
}
```

逻辑上是 1 -> 2 -> 3，但通过has-child查询时，不能得到文档2。

```json
{
  "query": {
    "has_child":{
        "type": "my-child",
        "query": {
            "match_all":{}
        }
    }
  }
}
```

而通过parent-id查询时，能使用2-3的父子关系。

```json
GET /my-index-000001/_search
{
  "query": {
      "parent_id": {
          "type": "my-child",
          "id": "2"
      }
  }
}
```

#### nested_query的匹配陷阱

子文档中只要有一个满足条件，父文档就会返回，即使其他子文档不符合条件。

例如

```json

PUT my-index/_doc/1?refresh
{
  "comments": [
    {
      "author": "kimchy"
    }
  ]
}

PUT my-index/_doc/2?refresh
{
  "comments": [
    {
      "author": "kimchy"
    },
    {
      "author": "nik9000"
    }
  ]
}

PUT my-index/_doc/3?refresh
{
  "comments": [
    {
      "author": "nik9000"
    }
  ]
}

POST my-index/_search
{
  "query": {
    "nested": {
      "path": "comments",
      "query": {
        "bool": {
          "must_not": [
            {
              "term": {
                "comments.author": "nik9000"
              }
            }
          ]
        }
      }
    }
  }
}
```

这样命中的文档为1、2，因为2中的kimchy是满足条件的。
要实现这种排除效果，需要把must_not放在外面。

```json
POST my-index/_search
{
  "query": {
    "bool": {
      "must_not": [
        {
          "nested": {
            "path": "comments",
            "query": {
              "term": {
                "comments.author": "nik9000"
              }
            }
          }
      }
      ]
    }
  }
}
```

### routing规则（es的具体实现，可能会变化）

es默认使用文档id进行routing。公式是

```plain

routing_factor = num_routing_shards / num_primary_shards
shard_num = (hash(_routing) % num_routing_shards) / routing_factor

```

可以在index和search时指定routing字段。也可以通过建立带有routing的alias，这样操作时自动会使用该routing。

如果只使用一个字段routing，可能会由于数据有规律导致数据倾斜。使用`index.routing_partition_size`解决。此时计算公式是：

```plain

routing_factor = num_routing_shards / num_primary_shards
routing_value = hash(_routing) + hash(_id) % routing_partition_size
shard_num = (routing_value % num_routing_shards) / routing_factor

```

简单说就是通过id进行小范围的再routing。

**不要基于analyzied字段做聚合和排序？**  

## 原理与知识点

refresh, flush 分别是什么意思

文档被索引后，在索引段中的写入流程：内存缓冲区 -> 可被搜索的段，但未提交 -> 提交后写入磁盘

加入段通过refresh，默认1s一次。提交通过flush，默认30m一次。

30m一次提交间隔太久，可能会丢失索引数据。加入translog，记录索引的每一次操作，重启后通过回放translog恢复索引数据。translog也首先加入内存缓冲区，再写入磁盘。默认translog是每次request后提交磁盘，可以设置成异步提交，但会丢失数据。

segment里保存的是什么，是全量数据，还是增量数据，还是按照分词做hash？每次修改文档，会生成新的segment，这里又包含的是什么数据？

### es为什么要移除`mapping_type`

### es缓存

#### query filter

什么查询会构建缓存？缓存什么时候失效？有新数据写入会立即失效吗？

es缓存的是filter中产生的bitset，而不是filter查询的结果。缓存增量更新，有数据写入时会将新的文档加入缓存，所以不会“写后失效”。

并不是所有的filter都会缓存，比如term虽然是精确匹配，但考虑到直接查询的效率，es不会缓存。

默认情况下，根据使用频率决定是否缓存。构建成本比较高的查询，频率阈值相对低。

#### TF/IDF

词频：一个词在单个文档的某个字段中出现的频率越高，这个文档的相关度就越高。
逆向文档频率：一个词在所有文档某个字段索引中出现的频率越高，这个词的相关度就越低。

#### 字段中心 vs 词中心

对于如下查询

```json
{
   "query": {
      "multi_match": {
         "query": "peter smith",
         "type": "most_fields",
         "operator": "and",
         "fields": ["first_name", "last_name"]
      }
   }
}
```

从字段中心来看，查询逻辑如下：

(+first_name:peter +first_name:smith)
(+last_name:peter  +last_name:smith)

表示的是 peter,smith 都必须出现在相同字段。

词中心式 会使用以下逻辑：

+(first_name:peter last_name:peter)
+(first_name:smith last_name:smith)

表示的是 peter,smith 都必须出现，但是可以在不同字段。

同时cross_field的方式会混合计算不同字段的IDF，解决了单词在不同字段的IDF影响匹配精度问题。例子如下：

Peter是一个常见的名，Smith是一个常见的姓，都有较低的IDF。但如果有人的名是Smith，作为一个名来说不常见，导致有更高的IDF，评分可能会更高。
结果就是虽然搜索的是 "Peter Smith"，但 "Simth Williams" 的评分比 "Peter Smith" 的评分更高。

### es的refresh

使用默认值的1s和手动设置成1s有什么区别？
### es的resharding

- [ ] es的resharding过程？

- [ ] es为什么只支持倍数增加shard?

### global ordinals

什么是`ordinal`？为了方便聚合、排序，es保存了`docValue`，这是以docId为key的列式存储。为了节约存储空间，对列进行了压缩和映射，从而`docValue`实际保存的是压缩后的映射表的序号，这个序号就是`ordinal`。

但上面的`ordinal`是segment级别的，而聚合需要从整个shard中收集数据，所以es构建了一个shard级别的ordinal，即`global ordinals`。

需要注意，默认情况下es会在首次需要global ordinals时开始构建，根据数据的分布情况，可能会很影响查询性能。可以将mapping设置成主动构建`eager_global_ordinals=true`，这样每次shard发生变更时都会重新构建，只是这又会影响index性能。

此外，当字段的区分度很大时，例如uuid，构建global ordinals需要消耗大量内存。

可以在term聚合查询时，设置`execution_hit=map`，这样就不会再构建global ordinals，而是直接使用字段原始value，在内存中构建map进行聚合。


## 性能分析

### 数字类型的term查询

一个慢查询: select * from table where userId in (1,2,3) and status in (1,2)

通过分析profile，大部分耗时都在`status in (1,2)`这个子句的build_scorer阶段。

https://elasticsearch.cn/article/446

总结：高版本中数值类型会用 Block K-d Tree 储存索引，而不是老版本中的倒排。tree是按照value顺序保存，而不是倒排中按照docid排列。为了实现对doc的快速遍历，只能在tree上构建bitset，这个过程由于数据量，很慢。而将查询类型改成range后，es会使用`indexOrDocValuesQuery`优化，如果range的代价小，就按刚才的方式构建bitset；如果代价高，就利用DocValues，遍历其他子查询的结果，利用docid快速定位value进行过滤。

https://www.elastic.co/cn/blog/better-query-planning-for-range-queries-in-elasticsearch 在另一篇文章中提到，其他子句匹配到的doc越少，DocValues方式就越好，如果之前匹配到的太多，还不如用bitset。而这个案例通过userId匹配到的文档数很少，所以用DocValues更好。

原来官方文档都写好了。。。

> Mapping numeric identifiers
> Not all numeric data should be mapped as a numeric field data type. Elasticsearch optimizes numeric fields, such as integer or long, for range queries. However, keyword fields are better for term and other term-level queries.
> 
> Identifiers, such as an ISBN or a product ID, are rarely used in range queries. However, they are often retrieved using term-level queries.
> 
> Consider mapping a numeric identifier as a keyword if:
> 
> You don’t plan to search for the identifier data using range queries.
> Fast retrieval is important. term query searches on keyword fields are often faster than term searches on numeric fields.
> If you’re unsure which to use, you can use a multi-field to map the data as both a keyword and a numeric data type.

## 一些新概念

### data tier 数据分层

content tier: 内容节点，当将文档直接写入特定的索引时，直接进入content tier，数据会无限保留。

hot tier: 热数据层。写入data stream（数据流，管理时间序列数据，例如日志）时，首先进入热数据层。保存最近、最频繁访问的数据。该类型节点通常有更高的配置，且索引会配置一个或多个副本。

warm tier: 温数据层。一旦时间序列数据使用频率小于阈值，会迁移到温数据层。配置低于热层，但索引还是会有副本。

cold tier: 冷数据层。不再更新的数据从温层移动到冷层。仍然可以查询，索引一般无副本。

frozen tier: 冻结数据层。一旦数据几乎不再使用，会移动到冻结层

### index sorting

segment里保存的文档默认是没有排序保存的，使用`index sorting`可以指定文档按排序保存。

```json
PUT my-index-000001
{
  "settings": {
    "index": {
      "sort.field": "date", 
      "sort.order": "desc"  
    }
  },
  "mappings": {
    "properties": {
      "date": {
        "type": "date"
      }
    }
  }
}
```

可以使用这个技巧提前结束search。例如需要查createTime TopN的，如果index sorting也是按照createTime，那么只需要在每个segment查询topN个文档。

request中设置`"track_total_hits": false`可以进一步避免文档计数。

注意，聚合始终需要遍历所有符合条件的文档，不会受到`track_total_hits`的印象。

### Runtime Fields

是什么？怎么用？什么场景下需要用？优缺点？

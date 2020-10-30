# 为什么shuffle过程中要排序

在mr和spark的shuffle中，都需要在map和reduce端进行数据排序。为什么要排序？

在reduce端，需要将数据按照生成的key聚合处理。聚合的方式有两种，基于hash和sort。

基于hash一般是在内存中将数据分组。基于sort是将文件按key排序后，数据自然按key顺序排列。

所以在reduce端排序，是为了使用基于sort的聚合方式。在map端排序，是为了优化计算，不同mapper上的数据文件shuffle到reducer上后，如果每个小文件已经按key排序，reducer只需要进行一次归并合并就好。

## hash shuffle的问题和改进

基于hash的shuffle，过程中会产生很多小文件，map task * reduce task。

spark的优化方案是共享buffer。在每一个executor上，每个core可以执行多个task，每个core上的task共享buffer，每个buffer对应一个文件。

例如有100个executor，每个执行5个task，下一个stage有1000个task。那么产生的小文件数是 100 * 1000。

## spark sort shuffle

spark的sort shuffle进一步减少了map阶段的小文件数，每个task只会产生一个文件。

怎么区分每个task中不同的key分组呢？在spark中，是下一个stage的task主动读取上一个task生成的文件。生成文件时，也同时会生成一个索引文件，其中保存了文件偏移量与reduce task的对应关系。

### bypass机制

启动条件：

- 当partition数量小于阈值spark.shuffle.sort.bypassMergeThreshold时启动。
- 没有map端聚合。

bypass和hash类似，每个task也是先生成多个小文件，将数据根据key hash到不同的文件中，再进行一次文件合并同时生成索引文件。
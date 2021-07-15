# 复习zookeeper

## 带着问题

### zookeeper是怎么防止脑裂的

默认采用Quoroms，只有集群超过半数投票才能选举为leader。总数是写在配置文件中的，运行时不变。

此外还有基于权重的方式，当投票权重大于半数才能成为leader。

### zookeeper容错是什么意思？用的什么方案

### zookeeper是强一致的吗

不是，是顺序一致的。因为原生的zab算法中，写操作，只要有一半以上的follower节点成功了，就认为是操作成功。
此时如果读操作连到了还未写入的节点，读取的是旧值。
想要保证数据是最新的，必须手动调用sync()。

顺序一致是指，当一个客户端先写入a再写入b时，不会有其他客户端先看到b的值，再看到a的值。

#### 是怎么保证顺序一致的

zk提交分两步，proposal和commit。
proposal会发送给全部follower，每个follower一个队列。
当收到一半以上的ack后，发送commit。
follower接收commit，如果和最新的一个proposal的事务id不一致，follower退出并重新同步leader节点。

#### 一轮是什么意思？等所有投票都到，还是收到一个投票马上又发送一个投票

#### 投票箱是个什么结构，什么时候判断投票量大于1/2

### 顺带复习ACID和隔离级别

#### 关于一致性的理解

截止 2018-08-17，对一致性的理解偏向于：事务执行过程中其他事务无法看到其中间状态。
但是这个说法又涉及到了隔离性，还是无法完全确定定义。

### 扩展

#### 什么是undo、redo日志

#### 2PC 3PC

## ZAB

在zookeeper集群中，只有一台leader负责处理外部客户端的事物写请求，然后leader服务器将客户端的写操作数据同步到所有的follower节点中。
（读请求可能到任一节点，节点直接处理。写请求到达follower节点时，会转发给leader节点。）
只要超过半数follower节点反馈OK，Leader节点就会向所有的follower服务器发送commit消息。即将leader节点上的数据同步到follower节点之上。

### 消息广播

1. 客户端发起一个写操作请求。
2. Leader服务器将客户端的request请求转化为事物proposql提案，同时为每个proposal分配一个全局唯一的ID，即ZXID。
3. leader服务器与每个follower之间都有一个队列，leader将消息发送到该队列。
4. follower机器从队列中取出消息处理完(写入本地事物日志中)毕后，向leader服务器发送ACK确认。
5. leader服务器收到半数以上的follower的ACK后，即认为可以发送commit。
6. leader向所有的follower服务器发送commit消息。
7. follower收到commit后，将食物日志数据写入文件。

### 崩溃恢复

- ZAB协议需要确保那些已经在Leader服务器上提交的事务最终被所有服务器都提交。
- ZAB协议需要确保丢弃那些只在Leader服务器上被提出的事务。

基本过程如下：

服务器进行选举时投票主要包含两个信息：推举服务器的唯一标识和事务编号：服务器的ID和ZXID。服务器进行投票时发送消息自己的投票（ID,ZXID）到集群中的其他服务器，服务器在收到其他服务器发来的投票时，会和自己的投票进行比较，

1. 首先，比较事务ID，如果其他服务器的ZXID大于自己的ZXID，则更新自己的投票，自己的投票更新为其他服务器的投票（即收到的服务器发来的投票），并将的投票投出来集群中另外的服务器
2. 如果其他服务器的ZXID小于自己的ZXID，不用更新自己投票，保留自己原来的投票。
3. 如果其他服务器的ZXID与自己的ZXID相等，则比较服务器ID的大小，如果ID比自己的ID小，则保留自己的投票；如果ID比自己的ID大，则更新自己的投票为其他服务器的投票。

#### 恢复时数据同步问题

对于zk，下面的情况：leader提交proposal后，follower返回了ack，但是leader在发送commit命令前挂了，这个proposal是需要被应用到集群的。

是怎么保证这一点的呢？整个集群中除了原leader，没有节点实际提交了该proposal。在其他节点中这个proposal被保存到了磁盘，但是没有提交应用到内存中。如果重新选举时自己使用的ZXID是本节点中已经commit的事务id，那么就不会包含这样的proposal。

目前比较接受网上的一种说明：

> 重新加载本地磁盘上的数据快照至内存，并从日志文件中取出快照之后的所有事务操作，逐条应用至内存，并添加到已提交事务缓存commitedProposals。这样能保证日志文件中的事务操作，必定会应用到leader的内存数据库中。

这样是不是说明只要前leader提出的proposal只要到达了任意一个follower，下次选举时这个proposal都会被应用？这和需要过半才应用的规则不是有冲突吗？

## 选举算法

### 代码细节

#### 启动

[zk选举启动](../attach/zk选举启动.java)

##### 细节

org.apache.zookeeper.server.quorum.QuorumPeer#getVotingView() 这个保存了所有参与投票的机器。是在zk启动时初始化的。
org.apache.zookeeper.server.quorum.QuorumPeerMain#runFromConfig(QuorumPeerConfig config)

***recvset*** 用于保存一轮投票中，获取到的外来投票。
***recvset*** 当本地选票落后于外来选票时清空并添加外来选票，当本地选票领先于外来选票时不操作，当本地选票同步于外来选票时添加外来选票。

***outofelection*** 中保存的是heading和following选票。
如果选举周期不一致或者上面的leading校验没通过，会在outofelection中校验leading的有效性。
如果当前的本地投票周期落后，则更新为外来leading，这个逻辑没问题，但如果本地投票领先呢?

# 复习zookeeper

## 带着问题

### zookeeper是怎么防止脑裂的？

### zookeeper容错是什么意思？用的什么方案？

### zookeeper选举

#### 一轮是什么意思？等所有投票都到，还是收到一个投票马上又发送一个投票？

#### 投票箱是个什么结构，什么时候判断投票量大于1/2？

### 顺带复习ACID和隔离级别

#### 关于一致性的理解

截止 2018-08-17，对一致性的理解偏向于：事务执行过程中其他事务无法看到其中间状态。
但是这个说法又涉及到了隔离性，还是无法完全确定定义。

### 扩展

#### 什么是undo、redo日志

#### 2PC 3PC

## 选举算法

### 代码细节

#### 启动

[zk选举启动](../attach/zk选举启动.java)

##### 细节

org.apache.zookeeper.server.quorum.QuorumPeer#getVotingView() 这个保存了所有参与投票的机器。是在zk启动时初始化的。
org.apache.zookeeper.server.quorum.QuorumPeerMain#runFromConfig(QuorumPeerConfig config)

***recvset*** 用于保存一轮投票中，获取到的外来投票。
***recvset*** 当本地选票落后于外来选票时清空并添加外来选票，当本地选票领先于外来选票时不操作，当本地选票同步于外来选票时添加外来选票。

***outofelection*** 中保存的是heading和following选票。
如果选举周期不一致或者上面的leading校验没通过，会在outofelection中校验leading的有效性。
如果当前的本地投票周期落后，则更新为外来leading，这个逻辑没问题，但如果本地投票领先呢?
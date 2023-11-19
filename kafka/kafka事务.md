# kafka事务

主要是解决跨分区消息的原子性。

## 基本使用流程

producer.initTransactions();
producer.beginTransaction();
producer.send(record1);
producer.send(record2);
producer.commitTransaction();

消费端两种隔离级别，READ_COMMITTED, READ_UNCOMMIT，只有前者事务才会生效，否则全部都能消费到，无论事务未提交还是abort。

## TransactionalID, ProducerID, epoch

tid是客户端提供的一个事务唯一标识符。一般一个producer对应的就是一个tid，即使producer重启，只要tid不变，也认为是同一个事务。这样可以避免因producer的重启导致事务悬挂。同时，只要人工改变了tid，就可以成为一个新的producer。

pid是kafka内部的标识符，也表示一个唯一的producer。一般tid和pid是一一对应的。如果有tid对应多个pid，那么通过epoch判断并保留最新的的一个producer。

pid主要用于发送消息时的幂等性。broker和producer都会维护一个seqNum，仅当producer发送的seqNum大于等于broker的seqNum时才会接收消息。broker维护的是 pid-topic-partition 维度的seqNum，所以幂等性其实只支持到某个produer的单个session的单个分区。

## 事务的实现

在事务内发送的消息，事务标志位会置为true。在事务结束时，会发送一个控制消息(COMMIT/ABORT)给写入的每个分区的leader节点。

（对于事务中的内部主题，例如 _transaction_state_, _consumer_offset_等，暂不讨论）

为了避免consumer提前拉取到没有结束的事务消息，kafka引入了LSO(Last Stable Offset)，表示一个位置，在此位置之前的事务状态已经确定。（没有事务的消息，LSO=HW）

好处是consumer端不会提前拉取未结束事务消息，不需要做类似缓存的操作。坏处是在事务结束前写入的非事务消息，也无法消费。

关于abort消息的处理，kafka是放在了consumer端。server端保存了abort transaction信息，consumer拉取数据时，server会把拉取范围内涉及到的abort事务集合一并返回。consumer根据该集合判断是否丢弃消息。


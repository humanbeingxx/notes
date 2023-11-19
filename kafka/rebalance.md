# Rebalance

## 触发时机

consumer必须是按照topic或者pattern方式消费，即不能手动指定分区消费。

每次poll之后，都会校验是否需要rebalance。

1. 之前分配过分区，但是本次poll发现metadata发生了变化。
2. 上一次join后，订阅信息发生了变化。

此外提交offset时，broker也会校验当前状态是否需要rebalance。具体的是，当group状态处于stable或者preparing时，可以正常提交offset，当处于completing时，会返回一个REBALANCE_IN_PROGRESS错误，让consumer自行处理。

所以rebalance会导致消息重复消费。

*此外，为什么preparing阶段会允许提交呢？*  因为broker并不会主动通知consumer状态变更，而是通过给consumer的heartbeat的response捎带当前group的状态，也就是说在下次heartbeat来之前，对于consumer来说都是stable状态，所以应该可以正常提交offset。

## 流程

入口是 org.apache.kafka.clients.consumer.KafkaConsumer#poll(org.apache.kafka.common.utils.Timer, boolean)

在poll过程中会首先校验是否更新分区分配信息。

1. findCoordinator阶段
   1. ensureCoordinatorReady方法，查找Broker上GroupCoordinator。

2. joinGroup阶段
   1. Broker收到请求后计算coordinator所在位置，具体在`kafka.server.KafkaApis#handleFindCoordinatorRequest`
   2. 调用本地的joinGroupIfNeeded方法，发送joinGroup请求。
   3. Broker收到所有的join请求后，返回join成功，并选择其中的一个（应该是第一个加入的）consumer作为leader。给leader的返回数据中包含了订阅信息，leader负责消费分配方案的制定。

3. syncGroup阶段
   1. 根据join的结果，判断自己是否是leader节点。
   2. 如果是leader，执行分配逻辑，并发送给GroupCoordinator。
   3. 如果是follower，发送空分配给GroupCoordinator。
   4. Broker收到syncGroup请求后，判断是否是leader，如果是，暂存分配结果，并将sync的状态改成stable，这样后面来的follower能直接拿到分配结果。如果是在leader之前来的follower请求，会等待，在leader到来时唤醒。

4. 更新offset阶段
   1. 首先尝试将offset更新为上次的committedOffset。可能不成功，比如分区经过rebalance分配给了其他consumer。
   2. 采用默认策略更新offset，对应配置'auto.offset.reset'。

5. heartbeat阶段
   1. 在joinGroup开始之前会将heartbeat停止，防止干扰rebalance过程。
   2. join成功后重新打开heartbeat。

## 触发情景

1. 新成员加入组。Coordinator收到join请求后，判断是newMember加入，会将状态更改为PreparingRebalance。这里主动并不会通知其他consumer，而是等consumer下次poll或者heartbeat时，返回对应的状态。
2. 老成员退出。情况很多，包括consumer下线、consumer的poll超时（伴随着heartbeat超时）、unscribe。

## 减少不必要的rebalance

订阅成员的增加减少带来的rebalance不可避免，重点是减少没有变动情况下异常rebalance。

首先是心跳，consumer和broker通过心跳识别是否存活，heartbeat.interval.ms控制心跳间隔，session.timeout.ms控制每个会话的存活时间。可以提高心跳发送频率，避免意外离组。

其次是消费者主动离组，max.poll.interval.ms控制两次poll之间的最大时间间隔，如果超过这个时间会认为当前自己的消费能力不足，主动离组。
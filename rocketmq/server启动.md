# server启动

## 启动`MessageStore`

首先是加锁，保证值开启一个server。

其次调整offset，调整为所有队列中的最大物理偏移量，如果没有队列，则设置为commitlog的最小偏移量。作为后续reput操作的起始读取位置。

启动`ReputMessageService`。[reput作用](###ReputMessageService)

启动长轮询request定时任务。

向nameserver注册broker，同时定时重新注册。

开启事务消息检测定时任务。[检测方式](./事务消息.md##半消息定时扫描)

如果是slave，开始消息同步任务。

此外还有一些监控、统计类的后台任务。

### ReputMessageService

总体上，reput操作是读取一批消息，然后分别构建ConsumeQueue和Index，还有一个不知道什么用的bitmap。

reput是一次读取从start开始，到commitlog最大写位置，也就是全部可读的数据，然后每次读取buffer中的一条数据处理。

index是相对复杂的结构，[存储结构](../attach/rmq存储结构.jpeg)，文件前部分是500w个entry的入口，后面是顺序实现的每个entry的链表。链表是倒序插入，最后一条数据保存了前一条数据的offset。

### 长轮询定时任务 `PullRequestHoldService`

当有新消息写入后，会通知`ReputMessageService`，其中会通知`NotifyMessageArrivingListener`。listener会直接触发一次长轮询处理。

同时经过一定时间等待，开始扫描缓存的pullRequest，处理其中满足offset条件的pullrRequest。

这里的处理，并不是解析消息后者将写入的消息直接返回给等待的请求，而只是通知PullMessageProcessor去处理未完成的请求。

## 启动`remoteServer`

主要是开启一个nettyserver。

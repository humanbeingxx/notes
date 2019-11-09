# spark

## 概念与理论

### 架构

![架构图](../attach/spark集群架构图.png)

#### 概念

##### Driver

主要完成任务的调度以及和executor和cluster manager进行协调。有client和cluster模式。client模式driver在任务提交的机器上运行，而cluster模式会选择机器中的一台机器启动driver。driver会执行代码中的main方法，创建SparkContext，并将application中的代码发送给executor，最后SparkContext会将task发送给executor执行。

##### Worker

集群中可以运行任务的节点。

##### Executor

worker中运行任务的节点。

##### Job, Stage, Task

可以认为每一个action都会产生一个job。
一个job会拆分成不同stage。
一个stage会分成不同的task。task是被executor执行的最小单元。

```mermaid
graph TB
Job--stage划分算法--> Stage
Stage--scheduler调度单位-->TaskSet
TaskSet--task最佳位置算法-->Tasks
```

job划分stage最重要的原则`从宽依赖处分割`。
task的数量由partition数量决定。
DagScheduler将stage封装成taskSet。

#### 宽窄依赖

窄依赖: 父RDD仅被一个子RDD依赖。
宽依赖: 父RDD不同数据被不同子RDD依赖。

cartesion(笛卡尔积)方法中，虽然父RDD被不同子RDD依赖，但仍然是窄依赖。`个人理解是因为不需要shuffle，而是将每个RDD都拷贝到其他RDD`

#### spark中常见操作

### spark shuffle过程

## 核心模块

### hdfs数据读取

spark是怎么读取文件的？是按照文件流的方式读取文件，还是说一次读取一个文件块，加载到内存？有shuffle和没有shuffle的时候，读取时怎么进行的？

Spark中的Task只有两个子类，一个是ResultTask，一个是ShuffleMapTask。当需要有shuffle的时候，会生产一个或者多个ShuffleMapTask。任何一个有输出的Job，都会生成ResultTask，用于计算出结果。

对于一个简单的count程序，spark经过一系列初始化和运行后，会直接进入ResultTask。

#### 文件split数量计算

首先分析spark是如何计算出读取文件split数量的：

1. SparkContext.runJob之后，会调用Rdd的partitions方法得到partition信息，最终调用的是HadoopRDD.getPartitions
2. HadoopRDD调用`getInputFormat(jobConf).getSplits(jobConf, minPartitions)`，这里用的是FileInputFormat
3. 遍历目录下所有文件，获取每个文件的blockLocations（每个文件块）。计算出读取时文件块的目标大小，公式是`Math.max(minSize, Math.min(goalSize, blockSize))`。如果有1000个1M的小文件，最终会生成1000个split，如果是1个1G的大文件，最终会生成8个128M的split。

spark中，将hdfs文件抽象成了FileSplit，记录了文件名，偏移量，host等信息。

#### split和block一些疑问

- spark生成的split和hdfs的物理block是什么对应关系？
- 如果spark设置的最小split是200M，会怎么处理？一个split装了128M的block，剩下的72M怎么办，和其他split共享一个block吗？
- split和partition的关系是怎么样的？

#### 具体的文件读取

Executor开始执行后，如下执行流程：

1. org.apache.spark.executor.Executor

    ```scala
    val res = task.run(
        taskAttemptId = taskId,
        attemptNumber = taskDescription.attemptNumber,
        metricsSystem = env.metricsSystem)
        threwException = false
        res
    }
    ```

2. org.apache.spark.scheduler.ResultTask

    ```scala
    //这里的func是反序列化生成，没有看到具体实现
    func(context, rdd.iterator(partition, context))
    ```

3. org.apache.spark.rdd.MapPartitionsRDD#compute -> org.apache.spark.rdd.HadoopRDD#compute

每一个executor运行一次run，处理的是一个split。具体读取hdfs就在HadoopRDD#compute中。

compute中通过InputFormat得到RecordReader，测试时用的分别是FileInputFormat和LineRecordReader。
具体的从block读取数据的过程，由DFSInputStream实现，待补充。

尝试解答一下[split和block一些疑问：](####split和block一些疑问)

- spark中的split和hdfs的block没有直接的对应关系，split只是记录了文件和对应的读取偏移量，最终落到哪个block，是由hadoop底层计算得到的。
- 网上说split不能跨文件，也就是两个split不能共享一个文件，这个待确认。这里说的文件应该是hdfs上的一个文件，而不是一个block。
- 一个split对应一个task，如果没有shuffle，那么每个resultTask生成一个partition。map阶段partition数量不变，但是reduce阶段会变化，和具体的操作有关。

#### RecordReader

对于普通的text文件，使用的是LineRecordReader，按行读取数据。

org.apache.hadoop.mapred.LineRecordReader#next
    org.apache.hadoop.util.LineReader#readLine(这里并不是一次读取一行，而是读取一定字节数并缓存起来，每次读取一行是从缓存中读字节直到一个换行符)
        org.apache.hadoop.hdfs.DFSInputStream#read
            org.apache.hadoop.hdfs.DFSInputStream#readWithStrategy

在readWithStrategy中，`currentNode = blockSeekTo(pos);`来根据读取的位置确定是哪一个block。

### Executor将数据传输给Driver过程

```scala
  def runJob[T, U: ClassTag](
      rdd: RDD[T],
      func: (TaskContext, Iterator[T]) => U,
      partitions: Seq[Int]): Array[U] = {
    val results = new Array[U](partitions.size)
    runJob[T, U](rdd, func, partitions, (index, res) => results(index) = res)
    results
  }
```

这里会new一个array用来存放执行结果。继续跟踪，会发现这个ResultHandler `(index, res) => results(index) = res`会传给JobWaiter。它在一个EventLoop中，监听receive事件，接受executor返回的结果。当taskSucceeded时，调用ResultHandler将结果放入array中。

下面看Executor是怎么发送结果的。

Executor执行方法是 org.apache.spark.executor.Executor.TaskRunner#run。当执行完一系列计算后，会将result序列化成ByteBuffer，再通过ExecutorBackend，将buffer发送出去。发送实际使用的是netty(NettyRpcEnv)。根据发送目的地址是不是本地，会选择postOneWayMessage还是postToOutbox。

### Spark Rpc 模型

最基本的使用方法

```scala
//server端创建
val config = RpcEnvServerConfig(new RpcConf(), "hello-server", "localhost", 52345)
val rpcEnv: RpcEnv = NettyRpcEnvFactory.create(config)
val helloEndpoint: RpcEndpoint = new HelloEndpoint(rpcEnv)
rpcEnv.setupEndpoint("hello-service", helloEndpoint)
rpcEnv.awaitTermination()

//client端使用
val rpcConf = new RpcConf()
val config = RpcEnvClientConfig(rpcConf, "hello-client")
val rpcEnv: RpcEnv = NettyRpcEnvFactory.create(config)
val endPointRef: RpcEndpointRef = rpcEnv.setupEndpointRef(RpcAddress("localhost", 52345), "hell-service")
val future: Future[String] = endPointRef.ask[String](SayHi("neo"))
future.onComplete {
    case scala.util.Success(value) => println(s"Got the result = $value")
    case scala.util.Failure(e) => println(s"Got error: $e")
}
Await.result(future, Duration.apply("30s"))
```

#### RpcEnv

对于server side来说，RpcEnv是RpcEndpoint的运行环境，负责RpcEndpoint的整个生命周期管理，它可以注册或者销毁Endpoint，解析TCP层的数据包并反序列化，封装成RpcMessage，并且路由请求到指定的Endpoint，调用业务逻辑代码，如果Endpoint需要响应，把返回的对象序列化后通过TCP层再传输到远程对端，如果Endpoint发生异常，那么调用RpcCallContext.sendFailure来把异常发送回去。

对client side来说，通过RpcEnv可以获取RpcEndpoint引用，也就是RpcEndpointRef的。

有两个重要方法:

- `org.apache.spark.rpc.RpcEnv#setupEndpoint` 注册一个RpcEndpoint。具体的会在Dispatcher的endpointRefs中保存Endpoint和它对应的一个EndpointRef。
- `org.apache.spark.rpc.RpcEnv#setupEndpointRef` 获取一个EndpointRef（虽然方法名是setup，实际是一个生成新ref的操作）。具体的会根据参数中的address生成一个EndpointRef，同时用一个RpcEndpointVerifier校验该ref是否存在。*这是一个同步方法，在校验完成前不会返回*

#### RpcEndpoint

一个RpcEndpoint更像是一个响应请求的server，看它的几个核心方法：

- `org.apache.spark.rpc.RpcEndpoint#receive` 接受一个消息，不回复
- `org.apache.spark.rpc.RpcEndpoint#receiveAndReply` 接受一个消息，回复

从这两个方法来看，都是接受消息，所以行为更像是一个server端。

#### RpcEndpointRef

ref的核心方法包括：

- `org.apache.spark.rpc.netty.NettyRpcEndpointRef#send` 发送一个消息，不需要回复
- `org.apache.spark.rpc.netty.NettyRpcEndpointRef#ask` 发送一个消息，需要回复

新版spark，ref就一个实现，NettyRpcEndpointRef。发送消息时，如果是发送给本地，则直接通过dispatcher放入本地的inbox中并通知处理；如果是发送给远程，则放入本地的outBox中，异步发送。

不同endpoint的ref，其不同只是持有的address不同，实际上就是只有通信地址的不同。

#### RpcEndpoint和RpcEndpointRef的关系

虽然ref这个后缀看上去很像是指针类似的引用，或者至少是某种形式的包装，实际上并没有这么直接的关系。

endpoint在注册到RpcEnv时，会同时在RpcEnv中生成并保存一个对应的ref，这个ref的address是RpcEnv中设置的address。

就我目前看到的，获取ref有两种方式。

- endpoint中通过self获得自己的ref。是从dispatcher保存的关系中直接获取。
- 接受其他endpoint发送的消息，其中包含了发送发的ref信息。反序列化后得到远端的ref。例如 Master会发送RegisteredWorker消息给Worker，Worker拿到消息中master的ref，并修改本地保存的master信息。

##### 几对 Master Worker消息

```mermaid
graph LR
Worker--WorkerLatestState-->Master
Master--如果不认识该worker,KillExecutor-->Worker
```

```mermaid
graph LR
Driver--WorkerLatestState-->Master
Master--如果不认识该driver,KillExecutor-->Driver
```

```mermaid
graph LR
Worker--ExecutorStateChanged-->Master
Master--ExecutorUpdated-->Driver_App
```

```mermaid
graph LR
Worker--Heartbeat-->Master
Master--存在过该worker,但目前没有worker信息,ReconnectWorker-->Worker
```

##### 比较复杂的交互

```mermaid
graph LR
Worker--RegisterWorker-->Master
Master--Master处于StandBy,MasterInStandby-->Worker
Master--RegisterWorkerFailed_情况A-->Worker
Master--RegisterWorkerFailed_情况B-->Worker
Master--注册成功,RegisteredWorker-->Worker
```

图上情况A: master中已经包含了该worker的id，返回`Duplicate worker ID`

图上情况B: master注册时，发现该地址下已经有worker，且`state != UNKNOWN`，返回`Attempted to re-register worker at same address`

发送RegisteredWorker后，master会调用schedule方法，开始启动worker上的executor(org.apache.spark.deploy.master.Master#startExecutorsOnWorkers)。向worker发送LaunchExecutor，来给executor分配完资源，同时向driver发送ExecutorAdded。

```mermaid
graph LR
Master--LaunchExecutor-->Driver_App
Worker--ExecutorStateChanged-->Master
```

#### 从Master recovery过程看SparkRpc的使用

```sequence
Master->Master: 被选择为leader，发送ElectedLeader消息
Master->Master: registerApplication
Master->Application: 发送MasterChanged，将app状态置为UNKNOWN
Application->Master: 发送MasterChangeAcknowledged，master将app状态置为WAITING
Master->Master: registerWorker，将worker状态重置为UNKNOWN
Master->Worker: 发送MasterChanged
Worker->Master: 发送WorkerSchedulerStateResponse，master将该worker状态置为ALIVE
Master->Master: 发送CompleteRecovery
Master->Master: 开始completeRecovery，状态临时改为COMPLETING_RECOVERY
Master->Master: 移除全部UNKNOWN状态的worker
Master->Application: 向UNKNOWN状态的发送ApplicationRemoved_finished消息
Application->Application: stop
Master->Worker: 对于每个向UNKNOWN状态的app，向所有worker发送ApplicationFinished消息
Worker->Worker: maybeCleanupApplication
Master->Driver: 对于没有worker的driver，删除或者重启
Master->Master: 将WAITING状态的app置为RUNNING
Master->Master: 状态改为ALIVE
Master->Worker: schedule
```

removeWorkers的子流程

```sequence
Master->Driver: 对worker包含的每个Executor，向其对应的driver发送ExecutorUpdated_lost消息
Master->Master: 更新本地的executor信息
Master->Master: 对worker中包含的driver，重启或者删除driver，并执行schedule
Master->Application: 对每个不是Complete的app，发送WorkerRemoved消息
```

##### 存疑

- spark是怎么处理消息的延迟问题的？例如Master向Worker发送MasterChanged，怎么能保证及时收到Worker回复的WorkerSchedulerStateResponse？
- schedule是怎么样的过程？Master的removeDriver和schedule是怎么搭配工作的？

## 遇到的问题

### Task not serializable

由于Spark程序中的map、filter等算子内部引用了类成员函数或变量导致需要该类所有成员都需要支持序列化，又由于该类某些成员变量不支持序列化，最终引发Task无法序列化问题。

### spark-sql遇到 `Filtering is supported only on partition keys of type string`

用spark-sql执行下面语句 "select * from t where month in ('201903', '201904')" 会报错 `Filtering is supported only on partition keys of type string`
month是分区字段，int类型。相同的sql在hive中执行没有问题，但是在spark中报错。
网上有说将 hive.metastore.try.direct.sql=false，但是无效。

最终解决方案是用or代替in。

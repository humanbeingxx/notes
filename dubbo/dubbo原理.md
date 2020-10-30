# TODO:dubbo原理

基于dubbo V2.7.7

## spring中dubbo的启动

初始化基于spring event，监听ContextRefreshedEvent。启动调用 DubboBootstrap.start()。

### 初始化

1. 初始化配置中心相关配置，默认是将注册中心用作配置中心，RegistryConfig中的地址、协议、用户名等信息设置到ConfigCenterConfig。
2. 检查dubbo各个配置对象中的配置是否合法。
3. 初始化元数据服务`MetadataService`，定义了服务提供者和消费者的接口协议。

## dubbo服务导出

需要导出的服务，定义在ConfigManager中，每个service都会设置成一个`ServiceBean`，它继承自`ServiceConfig`，通过`export()`导出。

具体导出过程如下：

1. 检查和更新配置。包括 protocol、registry、泛化调用等配置的检查和更新。
2. 如果配置了delay，则使用调度线程执行，否则直接导出。
3. 进入ServiceConfig的doExportUrls()方法，这里只考虑普通情况，将服务按dubbo协议导出。
   1. 首先是获取定义的registry，校验参数后组装成url。例如配置为`<dubbo:registry address="multicast://224.5.6.7:1234" protocol="multicast" port="1234" />`，生成的url是`registry://224.5.6.7:1234/org.apache.dubbo.registry.RegistryService?application=meteor-consumer&dubbo=2.0.2&pid=9136&qos.enable=false&registry=multicast&release=2.7.7&timestamp=1603662243360`。
   2. 结合Protocal和Registry，将本服务导出。和上面的registry一样，service也是拼装成url，过程复杂，略。下面是一段服务生成的url。`dubbo://192.168.18.229:20880/priv.cxs.dubbo.TestService?anyhost=true&application=meteor-consumer&bind.ip=192.168.18.229&bind.port=20880&default=true&deprecated=false&dubbo=2.0.2&dynamic=true&generic=false&interface=priv.cxs.dubbo.TestService&methods=nothing&pid=9136&qos.enable=false&release=2.7.7&side=provider&timestamp=1603663001577`。
   3. 如果scope不是`remote`，则也导出到本地。此时的协议是injvm，所以通过SPI得到的是`InjvmProtocol`。协议生成一个`InjvmExporter`，并将这个exporter保存在自己的exporterMap中。这个map是全局共享的map。且ServiceConfig也会将这个exporter保存在记得的export列表中。*即使是导出到本地，也会在生成exporter时，过一遍filter链的构建，用多层filter将内部的invoker包装起来。*
   4. 如果scope不是`local`，则导出到外部。由`DubboProtocal`完成导出。首先会将exporter保存在本地的map中，再开启一个server。这里会判断是否已经开启了相同的server，也是保存在对应的serverMap中。该server最底层是用netty开启了一个服务。如果已经开启了服务，则调用对应的reset。*同样的，导出外部，invoker也会经过一遍filter链。*
   5. 由DubboProtocal导出的服务，通过直连就可以访问了，但是一般也会进行服务注册。注册由`RegistryProtocal`完成。其实在本案例中，是先进行`RegistryProtocal.export()`，其内部的`doLocalExport()`方法会调用`DubboProtocal.export()`进行服务导出。导出一般是到zk上，但是这里是用的广播（没有搭建zk）。注册到zk，其实也就是在zk上挂上一个url拼接成的临时节点。

## dubbo服务引用

## 调用过程

### 处理RpcContext

1. com.alibaba.dubbo.remoting.exchange.support.header.HeaderExchangeHandler#handleRequest中设置`DubboThread`

2. com.alibaba.dubbo.rpc.protocol.dubbo.DubboProtocol中设置`RemoteAddress`

3. ContextFilter中设置`Invoker(invoker + url), Invocation(methodName + parameterTypes + arguments), Attachments, LocalAddress`

## 常见的负载均衡

### 加权随机

### 加权轮询

### 一致性hash

### 最少活跃调用

## 集群容错

### failover

### failfast

### failsafe

## 服务目录组织与路由

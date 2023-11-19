# 配置

## 优先级

- 方法级优先，接口级次之，全局配置再次之。
- 如果级别一样，则消费方优先，提供方次之。

其中，服务提供方配置，通过 URL 经由注册中心传递给消费方。
建议由服务提供方设置超时，因为一个方法需要执行多长时间，服务提供方更清楚，如果一个消费方同时引用多个服务，就不需要关心每个服务的超时设置。

### 属性优先级

启动参数 -D > xml > properties

## 线程模型

Dispatcher

- all（默认） 所有消息都派发到线程池，包括请求，响应，连接事件，断开事件，心跳等。
- direct 所有消息都不派发到线程池，全部在 IO 线程上直接执行。
- message 只有请求响应消息派发到线程池，其它连接断开事件，心跳等消息，直接在 IO 线程上执行。
- execution 只请求消息派发到线程池，不含响应，响应和其它连接断开事件，心跳等消息，直接在 IO 线程上执行。
- connection 在 IO 线程上，将连接断开事件放入队列，有序逐个执行，其它消息派发到线程池。

## 集群容错

Failover 重试其他机器。
Failfast 立即失败。
Failsafe 失败忽略。
Failback 失败记录请求，定时重发。
Forking  并行调用多个服务，只要一个成功即可。
Broadcast 广播所有服务，任一失败即为失败。

## 多版本

当一个接口实现，出现不兼容升级时，可以用版本号过渡，版本号不同的服务相互间不引用。
可以按照以下的步骤进行版本迁移：

1. 在低压力时间段，先升级一半提供者为新版本。
2. 再将所有消费者升级为新版本。
3. 然后将剩下的一半提供者升级为新版本。

## 并发控制

将调用情况存在RpcStatus中，内部是一系列ConcurrentHashMap。（不配置executes或者actives时，貌似不会记录）
在Filter中生效，invoke前begin，后end。

获取RpcStatus时，是以url + method为维度的，url如下：

`dubbo://ip:port/Service?anyhost=true&application=f_qbcp&cluster=failfast&dubbo=3.2.11&interface=ProviderInterface&methods=MethodA,MethodB,MethodC&pid=_12345_&qapp=test&revision=1.0.31&side=provider&timeout=10000&timestamp=1540387289646&version=1.0.0`

url从Invoker中拿，methodName从Invocation中拿。

### executes

限制provider service的每个方法，服务器端并发执行（或占用线程池线程数）上限。

ExecuteLimitFilter

取RpcStatus中的active，也就是正在执行的量，判断大小。

### actives

限制provider service的每个方法，每客户端并发执行（或占用连接的请求数）上限。

ActiveLimitFilter

基本也是比较active，但是如果active>=max，会等待配置的timeout时间。时间结束后还是active>=max，抛异常。

## 连接控制


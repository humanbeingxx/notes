# 调用过程

## 处理RpcContext

1. com.alibaba.dubbo.remoting.exchange.support.header.HeaderExchangeHandler#handleRequest中设置`DubboThread`

2. com.alibaba.dubbo.rpc.protocol.dubbo.DubboProtocol中设置`RemoteAddress`

3. ContextFilter中设置`Invoker(invoker + url), Invocation(methodName + parameterTypes + arguments), Attachments, LocalAddress`
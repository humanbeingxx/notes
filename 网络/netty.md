# 学习netty

## JAVA NIO

### Buffer

// //TODO buffer的几个关键方法：flip, clear, compact

## 疑问

### LengthFieldBasedFrameDecoder

lengthAdjustment是什么含义和作用？
代码中的几个样例，解码的规则是怎么定义的？是不是先定义解码规则，再来个解码器解析里面的内容？

最后一个例子：

```plain
lengthFieldOffset   =  1
lengthFieldLength   =  2
lengthAdjustment    = -3 (= the length of HDR1 + LEN, negative)
initialBytesToStrip =  3

BEFORE DECODE (16 bytes)                       AFTER DECODE (13 bytes)
+------+--------+------+----------------+      +------+----------------+
| HDR1 | Length | HDR2 | Actual Content |----->| HDR2 | Actual Content |
| 0xCA | 0x0010 | 0xFE | "HELLO, WORLD" |      | 0xFE | "HELLO, WORLD" |
+------+--------+------+----------------+      +------+----------------+
```

这里为什么adjustment是-3？为什么不把initialBytesToStrip设置成4，直接可以获取到actual content。是因为需要保留HDR2吗？既然解码结果中已经没有了length这个数据，adjustment是多少还有什么意义，直接16-initialBytesToStrip不就结束了吗？

一个LengthFieldBasedFrameDecoder解析出来的内容应该怎么做二次解析？是根据这个decoder的规则再定义吗？还是有什么统一的公式可以直接获取到actual content？

`actualFrameLength = unadjustedFrameLength + lengthAdjustment + lengthFieldOffset + lengthFieldLength - initialBytesToStrip`

**应该这么理解:之前看的源码和资料，已经写出了inBuffer的实际长度，所以怎么看都觉得这些参数有点多余。但是实际运行中，并不知道inBuffer的总长度，而是根据解码器的各项参数去解析这个buffer。所以在代码中需要判断readableBytes的大小是否满足条件，同时buffer的字节数会超过一次解码需要的长度。从而上面的疑问也得到了解决，这一步decode只是将需要的数据提取出来，怎么使用这些数据是后面业务层的工作。**

### netty怎么解决半包问题

以简单的LineBasedFrameDecoder为例，是以换行符作为一个包的结束标志。那么是怎么处理连续不断的包呢？

在父类ByteToMessageDecoder中，有个`ByteBuf cumulation`用来缓存之前没有处理完的半包。

如下代码是ByteToMessageDecoder#channelRead针对cumulation的操作（去掉了部分其他操作代码），可以看出cumulation是当成了一个缓存使用。在finally中，当cumulation不可读时，就将它重置，表示目前没有需要处理的半包。

```java
try {
    ByteBuf data = (ByteBuf) msg;
    first = cumulation == null;
    if (first) {
        cumulation = data;
    } else {
        cumulation = cumulator.cumulate(ctx.alloc(), cumulation, data);
    }
    callDecode(ctx, cumulation, out);
} finally {
    if (cumulation != null && !cumulation.isReadable()) {
        cumulation.release();
        cumulation = null;
    }
}
```

callDecode将cumulation作为inBuffer，内部会调用具体的decode实现。LineBasedFrameDecoder的实现中，首先使用ByteBuf#getByte方法找到换行符，如果找不到，则返回null，null不会放到输出中。注意这里使用的是getByte方法，不会修改buffer的read index。从而如果是个半包，则不会修改cumulation的read index，后续读到的数据会追加到cumulation中，直到形成一个完整的包。同样的，如果读取中遇到了换行符，则会在读取数据中修改cumulation的read index，使得cumulation不会包含之前的数据包。

## netty NIO处理

### boss线程

boss线程用于监听accept事件。

下面看下boss线程的初始化：

1. 当netty初始化时，一般是在调用`serverBootstrap.bind`方法。
2. 内部会调用`AbstractBootstrap#initAndRegister`初始化，其中就有代码`ChannelFuture regFuture = group().register(channel);`。这里的group()拿到的是bossGroup（也有可能是boss、worker共用一个group）。
3. 经过多次调用链后，会到`io.netty.channel.AbstractChannel.AbstractUnsafe#register`方法，此时由于执行线程是外部线程，比如main线程，会用eventLoop执行具体的register操作。这里的eventLoop是bossGroup中的eventloop。
4. register0方法中最终会调用`selectionKey = javaChannel().register(((NioEventLoop) eventLoop().unwrap()).selector, 0, this);`。这里为什么interestops会是0呢？这里只是register，也就是做了个channel和selector的绑定，accept的设置是在后续完成的。
5. 这里的channel是`NioServerSocketChannel`，内部有个属性`readInterestOp`，默认初始化就是16(accept)。在register完成后，会调用`doBind`。这里主要是最终调用java底层的bind，`sun.nio.ch.ServerSocketChannelImpl#bind`。那么accept是这里设置的吗？sorry，还不是。
6. doBind之后会判断channel的状态是不是从!active变成了active，如果是，会再添加一个异步任务执行`pipeline.fireChannelActive();`
7. `DefaultChannelPipeline#fireChannelActive`判断channel是否是autoRead，是的话，会最终调用`io.netty.channel.nio.AbstractNioChannel#doBeginRead`。这里会将当前的readInterestOp设置到selector中，完成accept的设置。

*需要注意整个流程中线程的切换。在调用doRegister之前都是用户线程，也就是上面第3步之前。此后会向boss线程池中添加任务，并用boss线程执行*

监听到accept事件后，会将任务转发给worker线程。

1. 监听到accept事件，注意这里对key的判断是`readyOps & (SelectionKey.OP_READ | SelectionKey.OP_ACCEPT)) != 0`，read和accept都会处理。
2. 调用pipeline.read会走到`ServerBootstrap.ServerBootstrapAcceptor#channelRead`，此时会拿到childGroup，并register上当前的channel。
3. 执行时，由于使用的eventloop是child，但是当前线程是boss，所以会异步执行。（即使boss和worker是共用线程池，由于使用了eventLoopGroup.next方法，只要线程多于一个，还是会选择到另外的线程）。
4. 此时的channel是`NioSocketChannel`，内部的readInterestOp是1，也就是read。后续的流程和accept初始化差不多，只是由于当前worker线程，会立即执行。

#### 其他点

1. 为什么没有看到判断key.isWritable，或者注册监听write事件？
   > key.isWritable()是表示Socket可写,网络不出现阻塞情况下,一直是可以写的,所认一直为true.一般我们不注册OP_WRITE事件。
2. 网上有说boss线程池即使有多个线程，也只有一个会工作，这个是不对的。
   > 以NioEventLoop为例，每次执行完run，都会将自己再作为一个runnable传入给boss线程池，所以还是会选择池中的一个线程，不是只有一个。

# 学习netty

## JAVA NIO

### Buffer

// TODO: buffer的几个关键方法：flip, clear, compact

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

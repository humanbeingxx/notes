# Disruptor

这里直接看多producer多consumer场景。

## 核心组件

### 数据存储区 RingBuffer

一个环形数组，每个位置都预先填充，避免GC。

核心方法 next、publish 都是依赖于内部的Sequence实现。

### WorkerPool

每个pool内是协作消费，即消息只会被一个consumer消费。每个pool管理一组WorkProcessors。

pool中有一个workSequence，表示当前消费情况。每个processor共享这一workSequence，通过cas方式抢占消息。

每个processor内也有一个Sequence，表示当前processor的消费进度。主要用于记录当前实际消费最慢的进度是多少，例如有10个consumer，此时workSequence的消费进度已经是10了，但消费比较慢，实际一个消息都没有消费完毕，需要遍历每个processor的Sequence，判断当前最低消费进度是多少。

实现上，每个processor的Sequence会被传给RingBuffer中的Sequence，作为gatingSequences。当判断buffer是否还有可用空间、获取下n个槽时，都会遍历获得当前最小的消费进度。

### Sequence、Sequencer、Sequence Barrier

这三个并不是直接包含在Disruptor类中，而是在RingBuffer中，produer、consumer端都有用到。由于地位过于重要，可以认为是核心组件。

Sequence是一个序号生成器，可以看成一个优化过的AtomicLong，解决了伪共享问题。

Sequencer持有Sequence，用于操作Sequence，重要的实现有两个 SingleProducerSequencer和MultiProducerSequencer，后者处理了多producer时的并发问题。

Sequence Barrier主要用于producer和consumer之间的同步，最重要的能力是通知consumer何时有新数据，以及新数据的可读位置。

### WaitStrategy

consumer的等待策略，有很多种。但更重要的是定义了 waitFor 和 signalAllWhenBlocking 两个方法，是producer和consumer的同步媒介。

## 同步机制

## 杂记

### cacheLine和CPU伪共享

CPU从内存中并不止读取需要的字节数，而是按缓存行大小（一般是64字节）。

不同内存数据可能加载到不同CPU的缓存行中。例如cpu1和cpu2分别操作变量x、y，都会加载到自己的cacheline中。当cpu1修改x时，需要通知其他cpu，凡是缓存了x的cacheline都需要置为无效，导致cpu2虽然不用x，也需要重新读取y。

在java中可以使用 @Contended 注解，表示类或者字段独占一行。需要打开 -XX:-RestrictContended

### debug cas 显示不正确

测试ConcurrentLinkedQueue过程中，debug了offer方法的`p.casNext(null, newNode)`，但结果显示p.next被赋值成了p。

copy了ConcurrentLinkedQueue的代码，加了些sout，结果是正确的 p.next==newNode。

实际不是debug问题，而是debug过程中会调用queue的toArray方法，其中又会调用first()方法，导致修改了head和head.next。初始时head=tail，因此tail.next被设置成了tail本身。

```java
/**
    * Tries to CAS head to p. If successful, repoint old head to itself
    * as sentinel for succ(), below.
    */
final void updateHead(Node<E> h, Node<E> p) {
    if (h != p && casHead(h, p))
        h.lazySetNext(h);
}
```
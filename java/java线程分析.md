# java线程分析

## 某个dev机器上的线程状态如下

```java
90    java.lang.Thread.State: RUNNABLE
4    java.lang.Thread.State: TIMED_WAITING (on object monitor)
2    java.lang.Thread.State: WAITING (on object monitor)
10    java.lang.Thread.State: TIMED_WAITING (parking)
156    java.lang.Thread.State: WAITING (parking)
10    java.lang.Thread.State: TIMED_WAITING (sleeping)

```

### 简介状态的含义

#### java.lang.Thread.State: BLOCKED (on object monitor)

意味着它 在等待进入一个临界区 ，所以它在”Entry Set“队列中等待。

#### java.lang.Thread.State: WAITING (parking)

一直等那个条件发生。说明它在等待另一个条件的发生，来把自己唤醒，或者干脆它是调用了 sleep(N)。

#### java.lang.Thread.State: TIMED_WAITING (parking或sleeping)

定时的，那个条件不到来，也将定时唤醒自己。说明它在等待另一个条件的发生，来把自己唤醒，或者干脆它是调用了 sleep(N)。

#### java.lang.Thread.State: TIMED_WAITING (on object monitor) java.lang.Thread.State: WAITING (on object monitor)

获得了监视器之后，又调用了 java.lang.Object.wait() 方法。

> 每个 Monitor在某个时刻，只能被一个线程拥有，该线程就是 “Active Thread”，而其它线程都是 “Waiting Thread”，分别在两个队列 “ Entry Set”和 “Wait Set”里面等候。在 “Entry Set”中等待的线程状态是 “Waiting for monitor entry”，而在 “Wait Set”中等待的线程状态是 “in Object.wait()”。
当线程获得了 Monitor，如果发现线程继续运行的条件没有满足，它则调用对象（一般就是被 synchronized 的对象）的 wait() 方法，放弃了 Monitor，进入 “Wait Set”队列。

#### 如果大量线程在“waiting for monitor entry”

可能是一个全局锁阻塞住了大量线程。
如果短时间内打印的 thread dump 文件反映，随着时间流逝，waiting for monitor entry 的线程越来越多，没有减少的趋势，可能意味着某些线程在临界区里呆的时间太长了，以至于越来越多新线程迟迟无法进入临界区。

#### 如果大量线程在“waiting on condition”：

可能是它们又跑去获取第三方资源，尤其是第三方网络资源，迟迟获取不到Response，导致大量线程进入等待状态。
所以如果你发现有大量的线程都处在 Wait on condition，从线程堆栈看，正等待网络读写，这可能是一个网络瓶颈的征兆，因为网络阻塞导致线程无法执行。
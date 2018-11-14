# guava缓存

## 缓存应该有的一些特点

- 自动加载Entry（key-value对）到缓存中。
- 当缓存超过设定的最大大小时采用LRU算法进行缓存的剔除。
- 可设定缓存过期时间，基于最后一次访问或者最后一次写入缓存两种方式。
- keys自动使用WeakReference进行包裹。
- values自动使用WeakReference或者SoftReference进行包裹。
- 当Entry从缓存中剔除时会有通知机制。
- 能够对缓存的使用情况进行统计。

## guava缓存的刷新

### 定时过期

expireAfterWrite, expireAfterAccess

get时如果key不存在，会尝试加载，并且所有线程等会等待，但只有一个线程会执行load操作。

### 定时刷新

freshAfterWrite

当get时判断是否过期，如果过期则尝试刷新。只有一个线程会执行刷新，其他线程会返回旧值。

### 异步刷新

也是freshAfterWrite，但是load中用异步操作。所有get都立即返回旧值。

可以解决短时间内访问大量不同key的情况。

## 实现细节

### guava refreshAfterWrite是怎么做到一个线程更新，其他线程返回老数据？

每个线程get时，经过判断都会认为已过期，走到了 com.google.common.cache.LocalCache.Segment#insertLoadingValueReference 这个方法。
方法在在起始位置加了lock，ReferenceEntry中原本保存的是StrongEntry（默认是强引用），在获取到锁，一顿操作后，会将ReferenceEntry设置成LoadingValueReference，代码 `e.setValueReference(loadingValueReference);`。
在拿到锁后，有一段判断是`valueReference.isLoading() || (checkTime && (now - e.getWriteTime() < map.refreshNanos))`，StrongEntry返回false，LoadingValueReference返回true，如果此时entry没有超时，后面的线程判断始终满足valueReference.isLoading()，不会重新加载缓存。
注意，这里锁的只是`e.setValueReference(loadingValueReference);`，而不是加载缓存的过程。
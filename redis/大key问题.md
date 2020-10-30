# 大key问题

一个key对应的value特别大，包括单个string类型特别大，或者list、hash等类型包含的元素特别多。

读写大key时会导致超时，相关的删除和自动过期等操作也会影响性能。

解决方案：

1. string类型，
   - 如果每次都需要整体存取，可以将值按照规则拆分成多个key-value，然后用mget获取。
   - 如果只需要取部分值，可以拆分后保存到hash中，用hget、hset、hmset、hmget等操作。
2. 集合类型存储过多的元素，可以拆分成多个集合，或者分散到不同的redis实例上。

如果是key过多，可以考虑转hash存储，将原先的string类型保存到一个或多个hash中。
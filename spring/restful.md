# REST风格

## 问题

### No converter found for return value of type

我自定义了一个WebRet类用来包装返回结果，但是报了标题中的错。
boot的jackson-starter已经引入了jackson包，且默认注册了MappingJacksonMessageConverter。
我的问题出在WebRet没有get set方法。

### REST怎么做多维度查询

可以参考es的查询，仍然用get方法，但是参数用json传递。
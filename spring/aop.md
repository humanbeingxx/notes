# aop

## 代理模式与装饰器模式

经典的代理模式：spring aop。
经典的装饰器模式：java InputStream/OutputStream。

有什么区别？从上面两种方式可以看出，代理模式对于使用者来说是不可感知的，那么代理类和实际处理类应该对外暴露应该是一个接口。但从java流的实现来看，装饰器模式并不需要同一个接口。比如可以用DataInputStream包装FileInputStream，FileInputStream是标准的inputstream，而DataInputStream在此之上又实现了DataInput。
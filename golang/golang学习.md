# 学习笔记

- 如果一个类型实现了一个接口的所有方法，那么这个类型的实例就可以存储在这个接口类型的实例中，不需要额外声明。
- 下划线让编译器接受不使用的导入，并且 调用对应包内的所有代码文件里定义的 init 函数。

## interface

## 数值方法（value method）指针方法(pointer method)

数值方法可以用数值和指针调用。指针方法只能用指针调用。

```go
func (v A)() methodValue()  {
    //do xxx
}

func (p *A) methodPointer()  {
    //do xxx
}

func use()  {
    var a A
    var p *A

    a.methodValue() //✅
    p.methodValue() //✅

    a.methodPointer() //❎
    p.methodPointer() //✅
}
```

不能用value调用pointer method，原因

1. pointer method可以修改入参，但是如果参数是value，会复制原value，导致方法中的修改对原value无效。
2. 并不是任何时候都能拿到value的地址。例如 A{}.methodPointer()会报`cannot call pointer method on xx`和`cannot take the address of xx`

## 关于IO

### Reader

任何时候 Read 返回了读取的字节数，都应该优先 处理这些读取到的字节，再去检查 EOF 错误值或者其他错误值。

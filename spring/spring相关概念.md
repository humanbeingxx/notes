# 概念集

## IOC（控制反转）

传统编程中，需要有客户端来创建依赖对象，即使使用工厂方法等模式，最终还是会有大量耦合。
使用IOC技术，可以把创建和查找依赖对象的过程控制权移交给容器。

## DI(Dependency Injection))

是IOC的实现方式之一。
目标是解耦，使得客户端代码不会因为需要改变依赖的对象而发生变化。
一般来说，会有一些注入器，但是不允许客户端代码直接使用这些注入器，而是通过例如容器这种第三方调用注入器将依赖注入客户端。

## DI(Dependence Inversion Principle,依赖倒置原则)

这是设计模式中原则之一。

1. 高层次的模块不应该依赖于低层次的模块，他们都应该依赖于抽象。
2. 抽象不应该依赖于具体实现，具体实现应该依赖于抽象。

## 用到的设计模式

### 单例模式

### 代理模式

### 策略模式

例如实例化bean时使用`org.springframework.beans.factory.support.SimpleInstantiationStrategy#instantiate(org.springframework.beans.factory.support.RootBeanDefinition, java.lang.String, org.springframework.beans.factory.BeanFactory)`。
调用方通过指定不同策略，使用不同的实例化方法。

### 模板方法模式

JdbcTemplate的execute。通过传入不同的CallBack，来决定执行过程中具体执行和解析结果的动作。

### 简单工厂

例如创建bean时调用的`org.springframework.beans.factory.support.DefaultListableBeanFactory#getBean(java.lang.Class<T>)`。

### 工厂模式

FactoryBean，实现类有AbstractFactoryBean，SqlSessionFactoryBean，TransactionProxyFactoryBean等。具体返回什么类型的factory，由子类决定。

### 装饰器模式

BeanWrapper，添加了serPropertyValue的功能。

### 观察者模式

spring event。

### 责任链模式

mvc中的interceptor调用。

### 适配器模式

mvc执行过程中，会调用HandlerExecutionChain。对于不同的handler，需要使用不同的方式执行。例如handler可能有Servlet、HandlerMethod等各种，不同handle，执行方式不同，如果是Servlet，会简单调用service方法，如果是HandlerMethod，会有更复杂的调用链。
可以用多个if判断，确定用什么方式，但是众所周知这样不好。spring的方法是定义一系列adapter，包装不同handler场景。需要调用时，遍历adapter，如果当前adapter能处理这个handler场景，则使用。
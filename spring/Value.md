# @Value

## 赋值时机

创建bean后。

1. org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory#doCreateBean
2. org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory#populateBean
3. org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor#postProcessPropertyValues
4. org.springframework.beans.factory.annotation.InjectionMetadata#inject

## @Value("#{xx[xxx.xxxx]}")

### spring expression language

spring3.0引入了spring expression language(spel)语言,通过spel我们可以实现：

1. 通过bean的id对bean进行引用
2. 调用方法以及引用对象中的属性
3. 计算表达式的值
4. 正则表达式的匹配
5. 集合的操作

#### SpEL 字面量

整数：#{8}
小数：#{8.8}
科学计数法：#{1e4}
String：可以使用单引号或者双引号作为字符串的定界符号。
Boolean：#{true}
SpEL引用bean , 属性和方法：

#### 引用其他对象:#{car}

引用其他对象的属性：#{car.brand}
调用其它方法 , 还可以链式操作：#{car.toString()}
调用静态方法静态属性：#{T(java.lang.Math).PI}
引用配置文件字段：#{properties[key]}

#### SpEL支持的运算符号

算术运算符：+，-，*，/，%，^(加号还可以用作字符串连接)
比较运算符：< , > , == , >= , <= , lt , gt , eg , le , ge
逻辑运算符：and , or , not , |
if-else 运算符(类似三目运算符)：？:(temary), ?:(Elvis)

#### 正则表达式

```java
#{admin.email matches '[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,4}'}
```

#### 集合操作

@Value("#{cities.?[scale > 300]}") 按条件筛选
@Value("#{cities.^[scale > 300]}") 按条件筛选第一条满足的
@Value("#{cities.$[scale > 300]}") 按条件筛选最后一条满足的
@Value("#{cities.![scale]}") 抽取某一个字段

## @Value("${xxx}")

不需要指定具体加载对象，需要结合PropertyPlaceholderConfigurer使用。
PropertyPlaceholderConfigurer会将配置的所有给定值、文件等加载。
${xxx}中的xxx即为加载后配置的key

在跟踪源码后发现，无论是$还是#，都会将表达式代入表达式执行器中执行一遍。只是$代入时已经是替换好的值了。
所以可以发现，如果没有任何配置，@Value还是会有值，但是值是@Value("xxx")中的xxx原值。

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


## @Value("${xxx}")

不需要指定具体加载对象，需要结合PropertyPlaceholderConfigurer使用。
PropertyPlaceholderConfigurer会将配置的所有给定值、文件等加载。
${xxx}中的xxx即为加载后配置的key

在跟踪源码后发现，无论是$还是#，都会将表达式代入表达式执行器中执行一遍。只是$代入时已经是替换好的值了。
所以可以发现，如果没有任何配置，@Value还是会有值，但是值是@Value("xxx")中的xxx原值。

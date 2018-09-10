# @Value

## 赋值时机

创建bean后。

1. org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory#doCreateBean
2. org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory#populateBean
3. org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor#postProcessPropertyValues
4. org.springframework.beans.factory.annotation.InjectionMetadata#inject

## @Value("${xxx}")

需要结合PropertyPlaceholderConfigurer使用。
PropertyPlaceholderConfigurer会将配置的所有给定值、文件等加载。
${xxx}中的xxx即为加载后配置的key

## @Value("#{xx[xxx.xxxx]}")


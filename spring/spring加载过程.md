# spring加载

## xml加载过程

1. 获取对XML文件的验证模式
2. 加载XML文件，并得到对应的Document对象
3. 根据返回的Document对象注册bean信息

## bean加载过程

### 2. org.springframework.context.support.AbstractApplicationContext#refresh   -> org.springframework.context.support.AbstractRefreshableApplicationContext#refreshBeanFactory

```java
public void refresh() throws BeansException, IllegalStateException {
    synchronized (this.startupShutdownMonitor) {
        // Prepare this context for refreshing.
        prepareRefresh();

        // Tell the subclass to refresh the internal bean factory.
        ConfigurableListableBeanFactory beanFactory = obtainFreshBeanFactory();

        // Prepare the bean factory for use in this context.
        prepareBeanFactory(beanFactory);

        try {
            // Allows post-processing of the bean factory in context subclasses.
            postProcessBeanFactory(beanFactory);

            // Invoke factory processors registered as beans in the context.
            invokeBeanFactoryPostProcessors(beanFactory);

            // Register bean processors that intercept bean creation.
            registerBeanPostProcessors(beanFactory);

            // Initialize message source for this context.
            initMessageSource();

            // Initialize event multicaster for this context.
            initApplicationEventMulticaster();

            // Initialize other special beans in specific context subclasses.
            onRefresh();

            // Check for listener beans and register them.
            registerListeners();

            // Instantiate all remaining (non-lazy-init) singletons.
            finishBeanFactoryInitialization(beanFactory);

            // Last step: publish corresponding event.
            finishRefresh();
        }

        catch (BeansException ex) {
            // Destroy already created singletons to avoid dangling resources.
            destroyBeans();

            // Reset 'active' flag.
            cancelRefresh(ex);

            // Propagate exception to caller.
            throw ex;
        }
    }
}

```

#### 2.2. org.springframework.context.support.AbstractXmlApplicationContext#loadBeanDefinitions(org.springframework.beans.factory.xml.XmlBeanDefinitionReader)

使用XmlBeanDefinitionReader解析xml，加载bean定义。

#### 2.3 org.springframework.beans.factory.config.ConfigurableListableBeanFactory#preInstantiateSingletons

加载一些单例bean

#### bean创建过程

org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory#createBean()

#### 解决循环依赖

#### 插曲

spring会默认加载一些环境变量 environment = systemProperties + systemEnvironment
# spring加载

## xml加载过程

1. 获取对XML文件的验证模式
2. 加载XML文件，并得到对应的Document对象
3. 根据返回的Document对象注册bean信息

## bean加载过程

### 2. org.springframework.context.support.AbstractApplicationContext#refresh

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
            // 加载bean的核心步骤
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

org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory#createBean() 真正创建bean实例的地方。

判断类构造器和bean的构造方式。如果bean定义有构造参数 或者 autowire方式是AUTOWIRE_CONSTRUCTOR 或者 没有空的空白的构造器，都会走指定构造器。
否则，就用空白构造器，先实例化一个空白的对象，并用一个BeanWrapper包装。

org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory#populateBean 完成了对象内成员的赋值。

#### 解决循环依赖

实例化bean之前（org.springframework.beans.factory.support.DefaultSingletonBeanRegistry#beforeSingletonCreation），放入singletonsCurrentlyInCreation这个map。bean实例化出来后（org.springframework.beans.factory.support.DefaultSingletonBeanRegistry#afterSingletonCreation）再移除。

在 getSingleton的时候，spring的默认实现是，先从singletonObjects寻找，如果找不到，再从earlySingletonObjects寻找，仍然找不到，那就从singletonFactories寻找对应的制造singleton的工厂，然后调用工厂的getObject方法，造出对应的SingletonBean，并放入earlySingletonObjects中。

```java
// AAService和BBService循环依赖

@Service
public class AAService {

    @Resource
    private BBService bbService;
}

@Service
public class BBService {

    @Resource
    private AAService aaService;
}

```

bean生成的调用链是

前置

1. BBService先构造，从`org.springframework.beans.factory.support.AbstractBeanFactory#doGetBean`开始。
2. 调用`org.springframework.beans.factory.support.DefaultSingletonBeanRegistry#getSingleton(java.lang.String, boolean)`尝试从缓存中获取已经加载的bean，先singletonObjects，再earlySingletonObjects。
3. 都没有，则调用`org.springframework.beans.factory.support.DefaultSingletonBeanRegistry#getSingleton(java.lang.String, org.springframework.beans.factory.ObjectFactory<?>)`创建bean实例。
4. 方法`org.springframework.beans.factory.support.DefaultSingletonBeanRegistry#beforeSingletonCreation`会将BBService放入singletonsCurrentlyInCreation中，表示正在构建。
5. 调用`org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory#createBeanInstance`实际生成实例。这个方法会根据autwire、构造方法等情况，来选择如何实例出一个bean。
6. 创建完bean实例后，会调用`org.springframework.beans.factory.support.DefaultSingletonBeanRegistry#addSingletonFactory`将BBService的BeanFactory放入缓存singletonFactories中。
7. 调用`org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory#populateBean`开始填充BBService的依赖。此时会发现需要加载AAService。
8. 重复上面1~5步骤，当给AAService开始populateBean时，发现需要BBService。
9. 此时从缓存singletonObjects和earlySingletonObjects都没有发现BBService，但是可以获取到BBService的BeanFactory。获取BBService并将它放入缓存earlySingletonObjects中。同时从缓存singletonFactories中移除BBService。
10. 将BBService注入AAService后完成AAService的构建。
11. 调用`org.springframework.beans.factory.support.DefaultSingletonBeanRegistry#afterSingletonCreation`将AA从singletonsCurrentlyInCreation中移除。
12. 调用`org.springframework.beans.factory.support.DefaultSingletonBeanRegistry#addSingleton`将AAService从singletonFactories和earlySingletonObjects中移除，并加入singletonObjects中。
13. 继续执行BBService的populate，此时可以从singletonObjects中获取到AAService，完成。
14. 依次调用afterSingletonCreation和addSingleton，结束对BBService的处理。

为什么要一个ObjectFactory缓存？

如果没有这个，只能直接缓存一个bean的实例，但是我们知道，可能有aop等会将bean替换成代理类。如果缓存直接实例化的bean，那么这些代理类就没有机会替换了。
这里ObjectFactory的实现是：

```java
protected Object getEarlyBeanReference(String beanName, RootBeanDefinition mbd, Object bean) {
    Object exposedObject = bean;
    if (bean != null && !mbd.isSynthetic() && hasInstantiationAwareBeanPostProcessors()) {
        for (BeanPostProcessor bp : getBeanPostProcessors()) {
            if (bp instanceof SmartInstantiationAwareBeanPostProcessor) {
                SmartInstantiationAwareBeanPostProcessor ibp = (SmartInstantiationAwareBeanPostProcessor) bp;
                exposedObject = ibp.getEarlyBeanReference(exposedObject, beanName);
                if (exposedObject == null) {
                    return exposedObject;
                }
            }
        }
    }
    return exposedObject;
}
```

##### @Async循环依赖报错以及@Lazy解决

@Async参与到循环依赖时可能会报错。例如有A,B两个service，其中A用了异步。如果先初始化A，会报错"its raw version as part of a circular reference"。

- 报错的原因。

@Async将service中的方法改成异步执行，底层也是用代理完成。但是这个注解用了一个单独的processor，`AsyncAnnotationBeanPostProcessor`，会在执行`exposedObject = initializeBean(beanName, exposedObject, mbd);`时调用，生成一个代理类。此时返回的exposedObject是另一个对象，在后续执行到
`if (exposedObject == bean) { exposedObject = earlySingletonReference;}`时，等号已经不成立，开始走下面的判断，而默认`allowRawInjectionDespiteWrapping`是false，报异常。

解决上面的问题，可以在B中对A的依赖加上@Lazy。底层原理是注入时，如果是Lazy，那么生成一个代理，在getTarget()时才进行初始化。
具体的是`DefaultListableBeanFactory#resolveDependency`中会尝试生成一个lazy代理，`Object result = getAutowireCandidateResolver().getLazyResolutionProxyIfNecessary(descriptor, requestingBeanName);`，在实际获取target时，才调用beanFactory的`doResolveDependency`。由于没有提前调用`doResolveDependency`，因此也就不会生成A的早期实例，`DefaultSingletonBeanRegistry.earlySingletonObjects`中不会有beanA。

#### 插曲

spring会默认加载一些环境变量 environment = systemProperties + systemEnvironment

## web项目加载过程

1. web项目启动的时候，容器会优先读取web.xml文件，并且先找到listener和context-param两个节点；

2. 容器会创建一个ServlextContext上下文，并解析context-param节点，存入上下文中。

3. 容器创建listener实例，并执行listener实例中的contextInitialized(ServletContextEvent sce)方法。contextInitialized创建了spring父容器，在context-param中配置的spring.xml中的bean都会被加载到容器中。

4. 执行filter节点信息；

5. 最后创建servlet；

web.xml中的配置

```xml
<context-param>
    <param-name>contextConfigLocation</param-name>
    <param-value>classpath*:spring/app-core.xml</param-value>
</context-param>

<listener>
    <listener-class>org.springframework.web.context.ContextLoaderListener</listener-class>
</listener>
<listener>
    <listener-class>org.springframework.web.util.IntrospectorCleanupListener</listener-class>
</listener>


<servlet>
    <servlet-name>mvc-dispatcher</servlet-name>
    <servlet-class>org.springframework.web.servlet.DispatcherServlet</servlet-class>
    <init-param>
        <param-name>contextConfigLocation</param-name>
        <param-value>
            /WEB-INF/servlet-context.xml
        </param-value>
    </init-param>
    <load-on-startup>1</load-on-startup>
</servlet>
```

load-on-startup表示容器启动时，加载这个servlet。

### 创建父容器大致过程

1. 监听到servlet事件，启动 org.springframework.web.context.ContextLoaderListener#contextInitialized
2. 创建一个context实例 org.springframework.web.context.ContextLoader#createWebApplicationContext
3. 开始初始化 org.springframework.web.context.ContextLoader#configureAndRefreshWebApplicationContext
4. 调用refresh org.springframework.context.support.AbstractApplicationContext#refresh
5. 后面就是常规的加载过程。

### mvc容器创建过程

从DispatcherServlet的init()方法开始。

从load-on-startup到init()大致流程如下：

1. tomcat生成StandardContext实例 org.apache.catalina.mbeans.MBeanFactory#createStandardContext
2. tomcat将StandardContext添加到Host上 org.apache.catalina.core.StandardHost#addChild
3. org.apache.catalina.core.StandardContext#start
4. org.apache.catalina.core.StandardContext#loadOnStartUp
5. org.apache.catalina.core.StandardWrapper#loadServlet
6. org.apache.catalina.core.StandardWrapper#initServlet
7. org.springframework.web.servlet.HttpServletBean#init
8. org.springframework.web.servlet.FrameworkServlet#initServletBean
9. org.springframework.web.servlet.FrameworkServlet#createWebApplicationContext(org.springframework.context.ApplicationContext)
10. org.springframework.web.servlet.FrameworkServlet#configureAndRefreshWebApplicationContext
11. org.springframework.context.support.AbstractApplicationContext#refresh

## spring的钩子

### BeanDefinitionRegistryPostProcessor

#### postProcessBeanDefinitionRegistry

ApplicationContext的refresh中，获取到BeanFactory之后 到 初始化bean 之前，调用 BeanDefinitionRegistryPostProcessor.postProcessBeanDefinitionRegistry。
可以通过这个钩子，动态添加、修改bean的定义。

#### postProcessBeanFactory

调用完BeanDefinitionRegistryPostProcessor.postProcessBeanDefinitionRegistry后会继续调用BeanDefinitionRegistryPostProcessor.postProcessBeanFactory。
也可以修改bean的定义。

#### 两者的区别？？

不是太清楚，注释中提到了，可以通过postProcessBeanDefinitionRegistry来修改其他的BeanFactoryPostProcessor实例。

### BeanPostProcessor

#### postProcessBeforeInitialization

doCreateBean方法中，在调用populateBean后，会调用initializeBean方法。
这个钩子在init前生效。

#### postProcessAfterInitialization

这个钩子在init后生效。

### InitializingBean

这个钩子在上面两个钩子之间生效。

### @PostConstruct

由CommonAnnotationBeanPostProcessor -> InitDestroyAnnotationBeanPostProcessor驱动执行。
也在postProcessBeforeInitialization过程中，看PostProcessor的先后顺序，InitializingBean之前。

总结下这几个的顺序。

postProcessBeanDefinitionRegistry > postProcessBeanFactory > postProcessBeforeInitialization 包含 PostConstruct > InitializingBean > postProcessAfterInitialization

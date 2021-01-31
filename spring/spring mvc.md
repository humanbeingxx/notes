# mvc

## 一个简单的get请求执行过程

![执行过程](../attach/mvc执行过程.png)

1. tomcat将请求发给DispatcherServlet，调用doService。DispatcherServlet经过一些处理后，调用doDispatch
2. 开始找HandlerMapping。HandlerMapping负责映射用户的URL和对应的处理类，执行过程中默认加载了两个RequestMappingHandlerMapping(处理）和BeanNameUrlHandlerMapping（基于beanName找映射，需要配置）。尝试RequestMappingHandlerMapping，会根据request中的url和已经加载的映射做匹配，匹配到了，返回RequestMappingHandlerMapping。
3. 找到合适的HandlerAdapter。主要是为了解决不同Handler不同处理方式。见 [spring概念](./spring相关概念.md#适配器模式)。开始执行。
    1. 先是HandlerExecutionChain.applyPreHandle，这里会执行interceptors的preHandle。
    2. 再是HandlerAdapter.handle。会执行ServletInvocableHandlerMethod#invokeAndHandle，invoke后会用注册号的returnValueHandlers来处理返回结果。不同的returnValueHandlers有不同的处理类型，根据这个来选择合适的handler。我们这个场景会选择到`并将结果写到输出流中，对于前端来说一个请求就完成了。
    3. 执行完后，是applyPostHandle。这里会执行interceptors的postHandle。
4. 见3.2。如果是要返回一个页面，执行的handler不一样。比如简单返回一个jsp，则是一个ViewNameMethodReturnValueHandler，将view的name设置到ModelAndViewContainer中。
5. 返回一个ModelAndView到DispatcherServlet。
6. 用加载好的ViewResolver解析view，项目中常配置InternalResourceViewResolver，解析内部资源。
7. 解析完成后返回一个View。
    1. 在返回给前端前，还有一些后续处理。调用HandlerExecutionChain#triggerAfterCompletion，会执行interceptors的afterCompletion。
    2. 发布一个ServletRequestHandledEvent。
8. 返回。

### RequestMappingHandlerMapping加载过程

实现了接口InitializingBean，在bean加载完成后会自动调用afterPropertiesSet方法，在此方法中调用了initHandlerMethods()来实现初始化。
过程就是遍历项目中的bean，遇到@Controller或者@RequestMapping的bean，根据RequestMapping的配置，生成配置信息RequestMappingInfo。再注册到MappingRegistry中。

### url匹配规则

RequestMappingHandlerMapping支持通配符匹配(指的是项目中的路径)。
优先级关系大致是

1. 更精确匹配更优先
2. ? 优先于 *
3. 更少的通配符优先于更多的通配符。

### 对于页面来说什么时候已经返回？？

当执行到  AbstractMessageConverterMethodProcessor.java:239，调用 `org.springframework.http.converter.HttpMessageConverter#write` 后，前端已经接受到结果。后面的处理是后端内部的事情了。

### 处理 @ResponseBody 返回中文乱码

此时StringHttpMessageConverter会生效，而默认是 text/plain, */*;charset=ISO-8859-1，所以中文乱码。

可以在`<mvc:annotation-driven>`中加配置（下面的*/*要慎重）

```xml
<mvc:annotation-driven>
    <mvc:message-converters>
        <bean class="org.springframework.http.converter.StringHttpMessageConverter">
            <constructor-arg index="0" value="UTF-8"/>
            <property name="supportedMediaTypes">
                <list>
                    <value>*/*;charset=UTF-8</value>
                </list>
            </property>
        </bean>
    </mvc:message-converters>
</mvc:annotation-driven>

```

加上后，会在默认加载的MessageConvertor前再加一个MessageConvertor。
要注意*/*，如果写成 text/plain，那 text/html 或者其他类型的请求仍然不会走这个convertor。
如果可以确定不要默认的convertor或者愿意手动copy一遍，可以设置 `<mvc:message-converters register-defaults="false">`

### springmvc DispathcerServlet默认配置

下面是经常用到的

```java

org.springframework.web.servlet.HandlerMapping=org.springframework.web.servlet.handler.BeanNameUrlHandlerMapping,org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping

org.springframework.web.servlet.HandlerAdapter=org.springframework.web.servlet.mvc.HttpRequestHandlerAdapter,org.springframework.web.servlet.mvc.SimpleControllerHandlerAdapter,org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter

org.springframework.web.servlet.HandlerExceptionResolver=org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver,org.springframework.web.servlet.mvc.annotation.ResponseStatusExceptionResolver,org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver

org.springframework.web.servlet.RequestToViewNameTranslator=org.springframework.web.servlet.view.DefaultRequestToViewNameTranslator

org.springframework.web.servlet.ViewResolver=org.springframework.web.servlet.view.InternalResourceViewResolver
```

### 返回一个viewName和json

返回一个json需要用@ResponseBody，这里会用RequestResponseBodyMethodProcessor处理返回结果，RequestResponseBodyMethodProcessor#handleReturnValue这个方法会直接在outputStream中写入数据，前端可以立即接受到返回值。

返回一个viewName会用到ViewNameMethodReturnValueHandler。
这个handler会拼好ModelAndView，在DispatcherServlet#render中使用。render首先使用注册好的viewResolvers定位到view，再调用view.render。最后通过tomcat返回给前端（这个是tomcat的源码）。

#### 一个知识点

spring默认使用jackson进行json序列化，对应的messageConverter是 MappingJackson2MessageConverter 。如果路径中有jackson相关类，会自动加载进来。在springboot-web中也是默认包含了jackson的。

当返回的对象，没有get方法时，会抛出一个“没有合适的converter”异常，但是实际上是有 MappingJackson2MessageConverter 的。原因是当 MappingJackson2MessageConverter 进行write时，发现对象type没有任何可以访问的属性（代码在com.fasterxml.jackson.databind.ser.BeanSerializerBuilder#build中），会使用一个UnknownTypeSerializer，最终会抛出异常。

#### 不同的handler

- ModelAndViewMethodReturnValueHandler 判断返回值是否是ModelAndView
- ModelMethodProcessor 判断返回值是否是Model
- ViewMethodReturnValueHandler 判断返回值是否是View
- HttpEntityMethodProcessor 判断返回值是HttpEntity，且不是RequestEntity。
- ModelAttributeMethodProcessor 判断方法上是否有ModelAttribute注解。这个处理器上annotationNotRequired==false
- RequestResponseBodyMethodProcessor 判断返回值方法上是否有ResponseBody注解。或者返回值所在容器（）是否有ResponseBody注解。
- ViewNameMethodReturnValueHandler 判断返回值是否是void或者CharSequence。
- MapMethodProcessor 判断返回值是否是map
- ModelAttributeMethodProcessor 这个处理器上annotationNotRequired为true，只要不是简单类型，都会判断为true。
- 其他。。。

## 高级知识点

### war包启动spring时，dispatchServlet是怎么和tomcat关联上的

用war包启动时，application需要实现`SpringBootServletInitializer`，通过依赖servlet3.0的自动扫描特性，完成加载。具体如下：

1. servlet3.0中，启动tomcat会通过java SPI的形式，调用所有配置过的`ServletContainerInitializer`的`onStartup`方法。在spring.web就在`org/springframework/spring-web/5.3.1/spring-web-5.3.1.jar!/META-INF/services`中配置了`org.springframework.web.SpringServletContainerInitializer`，所以启动容器时会自动执行该类的startup方法。
2. SpringServletContainerInitializer中，使用了注解`@HandlesTypes(WebApplicationInitializer.class)`，所有`WebApplicationInitializer`的实现类，都会被传入startup方法，依次被调用。而application实现的`SpringBootServletInitializer`就是实现了`WebApplicationInitializer`。
3. `SpringBootServletInitializer`的onStartup方法中，主要是构建了一个`SpringApplication`对象，然后调用run方法。
4. 在refresh时，会调用`createWebServer()`，但是由于此时拿到的`ServletContext`不是null(调用onStartup时作为参数传入的)，所以并不会用`ServletWebServerFactory`重新构建一个server，而是用一系列`ServletContextInitializer`进行初始化，其中就包含了`DispatcherServletRegistrationBean`。
5. `DispatcherServletRegistrationBean`会调用`servletContext.addServlet`将dispatcherServlet加载到context中。
6. 具体addServlet，是拿到context下的`StandardWrapper`，然后将servlet设置进去。这里的wrapper是和servlet的name对应的，每个不同名字的servlet都会有一个对应的wrapper。至此，servlet就注册到context中了。

### 请求进来时，tomcat到dispatcherServlet的过程

涉及到了tomcat的组件和请求过程，这块不清楚，暂时跳过。

1. 通过负责监听端口的connector，找到对应的StandardService，再找到对应的engine，然后拿到pipeline，开始执行。
2. 会执行到`StandardWrapperValve#invoke`，这里会通过`wrapper.allocate()`拿到servlet，即dispatcherServlet。
3. 调用allocate时，由于使用的不是'singleThreadMode'，所以会直接返回已经注册好的servlet，即单例的servlet。虽然不会重新生成servlet实例，但是dispatcherServlet默认是懒加载，各种mapping还没有配置，这里会进行初始化。可以修改spring配置`spring.mvc.servlet.load-on-startup=1`。

### spring websocket中bean和tomcat的关系

对于一般的请求，都是走DispatchServlet，然后找到对应的RequestMappingHandler，匹配后再处理，这里最终都会落到相应的Controller的方法中执行。

但是在ws中，作用方式和上述不一样。

spring配置如下：

```java
@Configuration
public class WebSocketConfig {

    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }
}

@ServerEndpoint("/ws/asset")
@Component
public class WebSocketServer {}
```

config，主要用于在tomcat中注册下面定义的server。具体的是`ServerEndpointExporter`实现了`SmartInitializingSingleton`，在其`afterSingletonsInstantiated`方法中，会进行endpoint的注册。这些endpoints就是在spring容器中，实现了ServerEndpoint注解的bean。
然后在ServerContainer中注册上bean对应的class。

建立ws过程中，根据ws请求的url路径，匹配到对应的server，然后new出一个server实例。也就是一个ws连接对应的其实是一个单独的server，不是bean。

上面可以看到，是通过ServerContainer来在spring和web容器中传递了endpoint信息，那么这个container是怎么来的呢？

1. spring加载时，生成普通bean之前，会调用`onRefresh(); // Initialize other special beans in specific context subclasses.`。
2. web项目中的context是`ServletWebServerApplicationContext`，其 onRefresh 会`createWebServer`。
3. 会生成一个tomcatServer，同时进行初始化，此时tomcat会调用 tomcat.start()。
4. 此时会一层层调用Lifecycle，最终会落到`TomcatEmbeddedContext.start()`。过程中，会创建一个`ApplicationContext`，并得到一个ServletContext。这里的context都是catalina下的context，而不是spring的。
5. 启动完成后，会调用context中的`initializers.onStartup()`，进行一些初始化的后置操作。这里是`TomcatStarter`。
6. `TomcatStarter`会进一步调用自己的`initializers`，这里会包含一个`ServletWebServerApplicationContext`的lamda表达式，最后会调用`selfInitialize(ServletContext servletContext)`，将servletContext保存。而这个WsServerContainer就是ServletContext的一个attribute。

下面看设置这个attribute的过程。

1. 其实就是在上面的5之后，会调用`listenerStart()`，ws下就是一个WsContextListener。调用`contextInitialized`方法进行初始化。
2. 如果`javax.websocket.server.ServerContainer`这个属性是null，则初始化一个。
3. 初始化很简单，就是`WsServerContainer sc = new WsServerContainer(servletContext);`，并加入attributes。

#### ws发送请求时，怎么和后端的server做的对应

总体来说，是在注册selector时，将server作为attachment传入了key中。当然这里不直接是server，外面有层层包装。包装顺序大致如下：

key -> socketWrapper -> currentProcessor -> internalHttpUpgradeHandler -> wsFrame -> textMsgHandler -> pojo(就是我们的server)

绑定过程（有推测成分）：

1. ws最初是用普通http建立的，在processor中包含了一个upgradeToken，最终会根军这个生成一个处理ws的process `UpgradeProcessorInternal`。
2. 处理io事件时，根据SocketWrapper的currentProcessor，拿到了`WsHttpUpgradeHandler`。
3. handler初次调用时，会init。其中就包含了根据url找到对应的server class，并生成实例的过程。

*有个容易混淆的点：SocketWrapper.currentProcessor和NioEndPoint$SocketProcessor，不是一个东西。可以理解成后者是一个调用框架，前者是具体的业务处理器，后者框架中会调用前者*

从tomcat架构来说，绑定过程大致是：acceptor接收到http的upgrading类型请求，然后生成了wrapper。再将wrapper保存到channel后，通过event机制注册到poller中去。poller处理event，初次处理ws对应的event时，ocketWrapper.currentProcessor是null，最初会生成一个http11Processor，然后根据process的结果状态（upgrading），又生成一个UpgradeProcessorInternal处理器，并保存到wrapper中。此后会调用上面3中说的init，生成server实例。最后，调用`NioSocketWrapper#registerReadInterest`，将wrapper注册到poller中，`getPoller().add(this, SelectionKey.OP_READ);`。
以后每次前端发数据，都会调用`NioSocketWrapper#registerReadInterest`将socket重新注册到poller中。

# mvc

## 一个简单的get请求执行过程

![执行过程](../attach/mvc执行过程.png)

1. tomcat将请求发给DispatcherServlet，调用doService。DispatcherServlet经过一些处理后，调用doDispatch
2. 开始找HandlerMapping。HandlerMapping负责映射用户的URL和对应的处理类，执行过程中默认加载了两个RequestMappingHandlerMapping(处理）和BeanNameUrlHandlerMapping（基于beanName找映射，需要配置）。尝试RequestMappingHandlerMapping，会根据request中的url和已经加载的映射做匹配，匹配到了，返回RequestMappingHandlerMapping。
3. 找到合适的HandlerAdapter。主要是为了解决不同Handler不同处理方式。见 [spring概念](./spring相关概念.md#适配器模式)。开始执行。
    1. 先是HandlerExecutionChain.applyPreHandle，这里会执行interceptors的preHandle。
    2. 再是HandlerAdapter.handle。会执行ServletInvocableHandlerMethod#invokeAndHandle，invoke后会用注册号的returnValueHandlers来处理返回结果。不同的returnValueHandlers有不同的处理类型，根据这个来选择合适的handler。我们这个场景会选择到StringHttpMessageConverter并将结果写到输出流中，对于前端来说一个请求就完成了。
    3. 执行完后，是applyPostHandle。这里会执行interceptors的postHandle。
4. 见3.2。如果是要返回一个页面，执行的handler不一样。比如简单返回一个jsp，则是一个ViewNameMethodReturnValueHandler，将view的name设置到ModelAndViewContainer中。
5. 返回一个ModelAndView到DispatcherServlet。
6. 用加载好的ViewResolverl解析view，项目中常配置InternalResourceViewResolver，解析内部资源。
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
# study boot

## 配置

boot配置优先顺序

- 命令行参数
- 来自java:comp/env的丹IDI属性；
- Java系统属性（System.getProperties());.操作系统环境变量：
- RandomValuePrope1tySource配置的random.＊属性值：
- jar包外部的application-{profile}.properties或application.yml（带spring.profile）配置文件：
- jar包内部的application-{profile}.properties或application.ym（带spring.profile）配置文件；
- jar包外部的application.properties或application.yml（不带spring.profile）配置文件：
- jar包内部的application.properties或application.ym（不带spring.profile）配置文件：
- @Configuration注解类上的＠PropertySource;
- 通过SpringApplication.setDefaultProperties指定的默认属性。

### 使用其他文件

定义 @PropertySource(value = {"classpath:config.properties"})

使用 @ConfigurationProperties("config")  这里的config是配置文件中的层级，例如config.name=test

### 条件装配

@Conditional(TestCondition. class)

## mvc

### 用mvn的tomcat启动boot，url路径的配置

在pom中可以配置为

```xml
<plugin>
    <groupId>org.apache.tomcat.maven</groupId>
    <!-- tomcat7的插件， 不同tomcat版本这个也不一样 -->
    <artifactId>tomcat7-maven-plugin</artifactId>
    <version>2.1</version>
    <configuration>
        <port>8080</port>
        <uriEncoding>UTF-8</uriEncoding>
        <path>/test_tomcat</path>
    </configuration>
</plugin>
```

在yml文件中可以配置

```yml
spring:
  mvc:
    servlet:
      path: /test_yml

```

可以同时生效，pom配置的优先于yml。pom中不配置的话，默认使用war包名。

### 端口的设置

如果pom和yml中都配置，以pom为准。如果pom没有配置，默认8080，用外部tomcat启动，yml中的server.port不生效。

## 问题

## @Resource @Autowired有什么区别

### @Resource

- 如果没有指定name和type，则优先按照名字，再按照类型查找。如果没有匹配的名字，且有多个同类型的bean，抛异常。如果名字和类型都找不到，抛异常。
- 如果指定了名字，不会再按类型查找，找不到抛异常。如果有多个类型的bean，会发生一些诡异的事。见下面。
- 如果指定了类型，如果有多个bean，会根据变量名再匹配，如果匹配不上，抛异常。如果没有，抛异常。

#### 指定name，有重复类型bean的情况

```java
// bean定义
@Bean(name = "config2")
public Map<String, String> getConfig2() {
    HashMap<String, String> map = Maps.newHashMap();
    map.put("key2.1", "value2.1");
    map.put("key2.2", "value2.2");
    map.put("key2.3", "value2.3");
    map.put("key2.4", "value2.4");
    return map;
}

@Bean(name = "config2")
public List<String> getConfig2_2() {
    return Lists.newArrayList("value1", "value2");
}

// bean使用

@Resource(name = "config2")
private List<String> configx;

// 此时configx不为null，而是一个List<Map<String, String>>。list里面是map类型的config2。
// 我的猜测，spring拿到了Map类型的bean，但是发现需要的是一个list类型，所以用list把map包装了一层。当然泛型被忽略了。
```

### @Autowired

- Autowired没有name或者type的参数，优先按类型匹配，有多个同类型bean，按照变量名匹配。用@Qualifier指定bean的name。
- 有required参数，允许为空。

### 不继承boot-parent怎么用？

用

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-dependencies</artifactId>
    <version>2.1.0.RELEASE</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

## Redis

### @Cacheable

`@Cacheable(value = "redisCache", key = "'job_' + #name")`

类似一个around操作。调用方法前，先尝试从缓存中获取，获取不到再调用方法。调用完成后，如果condition满足，则会将数据写入redis。
注解可以有另外两个参数，condition和unless。
condition只能用到入参上，unless能用到返回结果上。

```java
@Cacheable(value = "redisCache", condition = "#name.length() > 4",
        unless = "#result == null", key = "'job_' + #name")
public Job getOne(String name) {
    Job job = jobDao.selectOne(name);
    return job;
}
```

上面的例子，condition从参数判断结果是否写入redis，unless从结果判断是否写入redis（不满足unless条件的才写入）。

#### 为什么不能用condition判断result

condition的计算是在被代理方法之前进行的，此时用户计算condition的result对象是个常量`CacheOperationExpressionEvaluator.NO_RESULT`。并且在spel进行运算时，创建的运算符会判断操作数是不是这个常量，如果是，则result会设置为null。
所以一般的判断都不会满足。

#### condition能不能控制读取是否使用cache？

结果是能。
造个case，redis有key，db中没有，结果返回为空。

#### condition不满足，unless满足，能不能写入cache？

也不能。原因如下

```java
// 下面代码是 CacheAspectSupport#execute中condition判断执行的地方。
// 如果condition满足，会向这个put的list中放入一个操作。如果不满足就不会有put操作

List<CachePutRequest> cachePutRequests = new LinkedList<>();
if (cacheHit == null) {
    collectPutRequests(contexts.get(CacheableOperation.class),
            CacheOperationExpressionEvaluator.NO_RESULT, cachePutRequests);
}

// put时对unless的判断是在实际put操作时进行的 CacheOperationContext#canPutToCache
```

***由此可见condition控制的是整个方法，包括 读取 + 写入***

### cache的序列化

使用RedisCacheConfiguration配置。默认的序列化是JdkSerializationRedisSerializer。
如果要使用json，可以用FastJsonRedisSerializer。配置如下

```java
@Bean
public static RedisCacheConfiguration defaultCacheConfig() {
    // 这里必须开启autotype，并将类信息写入json，否则无法序列化成需要的类。
    ParserConfig.getGlobalInstance().setAutoTypeSupport(true);
    FastJsonConfig fastJsonConfig = new FastJsonConfig();
    fastJsonConfig.setSerializerFeatures(SerializerFeature.WriteClassName);

    FastJsonRedisSerializer<Object> fastJsonRedisSerializer = new FastJsonRedisSerializer<>(Object.class);
    fastJsonRedisSerializer.setFastJsonConfig(fastJsonConfig);

    RedisSerializationContext.SerializationPair<Object> serializer
            = RedisSerializationContext.SerializationPair.fromSerializer(fastJsonRedisSerializer);
    return RedisCacheConfiguration
            .defaultCacheConfig()
            .prefixKeysWith("cache_")
            .serializeValuesWith(serializer);
}

```
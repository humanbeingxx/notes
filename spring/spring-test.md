### spring-test context hierarchy

- merge

```java
@RunWith(SpringJUnit4ClassRunner.class)
@ContextHierarchy({
    @ContextConfiguration(name = "parent", locations = "/app-config.xml"),
    @ContextConfiguration(name = "child",  locations = "/user-config.xml")
})
public class BaseTests {}

@ContextHierarchy(
    @ContextConfiguration(name = "child",  locations = "/order-config.xml")
)
public class ExtendedTests extends BaseTests {}
```

加载后，会有三个context。app、user、user + order。app 分别是 user ， user + order 的parent

- override

```java
@RunWith(SpringJUnit4ClassRunner.class)
@ContextHierarchy({
    @ContextConfiguration(name = "parent", locations = "/app-config.xml"),
    @ContextConfiguration(name = "child",  locations = "/user-config.xml")
})
public class BaseTests {}

@ContextHierarchy(
    @ContextConfiguration(name = "child",  locations = "/test-user-config.xml", inheritLocations=false)
)
public class ExtendedTests extends BaseTests {}
```

加载后，会有三个context。app、user、order。app 分别是 user ，order 的parent

**但说到底，context继承有什么用呢？**

### TestPropertySource

PropertySource需要配置@Configuration使用，但是TestPropertySource可以单独使用。

dynamic > inline > location > @PropertySource/programmatically/OS properties/JVM properties/

### RecordApplicationEvents

可以记录测试过程中发生的所有event。在类上加注解 @RecordApplicationEvents，在要查看events时，使用 @Autowired ApplicationEvents events

### @Sql

@Sql({"/test-schema.sql", "/test-user-data.sql"}) 在test开始前执行两条sql。

### ActivateProfile

使用default定义默认配置.

### MockMvc

使用 MockMvc 测试controller

```java
@RestController
public class TestController {

    @GetMapping("/mvcTest")
    public String mvcTest(String name) {
        return new StringBuilder(name).reverse().toString();
    }
}

//test
mockMvc.perform(MockMvcRequestBuilders.get("/mvcTest").param("name", "cxs"))
        .andExpect(status().isOk())
        .andExpect(content().string("sxc"));
```

### context cache

从context参数生成cache-key。参数包含下列：

- locations (from @ContextConfiguration)
- classes (from @ContextConfiguration)
- activeProfiles (from @ActiveProfiles)
- parent (from @ContextHierarchy)
- propertySourceLocations (from @TestPropertySource)
- propertySourceProperties (from @TestPropertySource)
- contextLoader (from @ContextConfiguration)
- contextInitializerClasses (from @ContextConfiguration)
- contextCustomizers (from ContextCustomizerFactory) – this includes @DynamicPropertySource methods as well as various features from Spring Boot’s testing support such as @MockBean and @SpyBean.
- resourceBasePath (from @WebAppConfiguration)
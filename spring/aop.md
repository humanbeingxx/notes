# aop

## 代理模式与装饰器模式

经典的代理模式：spring aop。
经典的装饰器模式：java InputStream/OutputStream。

有什么区别？从上面两种方式可以看出，代理模式对于使用者来说是不可感知的，那么代理类和实际处理类应该对外暴露应该是一个接口。但从java流的实现来看，装饰器模式并不需要同一个接口。比如可以用DataInputStream包装FileInputStream，FileInputStream是标准的inputstream，而DataInputStream在此之上又实现了DataInput。

## 几个概念与关系

### Joinpoint 连接点

一个类或一段代码拥有一些边界性质的特定点，这些代码中的特定点就被称为“连接点”。

### Pointcut 切点

切点就是我们我们配置的满足我们条件的目标方法。

从spring源码可以看出，Pointcut是来判断一个方法是否需要切入。

```java
public Interface Pointcut {

    ClassFilter getClassFilter();

    MethodMathcer getMethodMatcher();
}

```

### advice

在一个Joinpoint处应该采取的动作。
常见的有 BeforeAdvice, AfterReturnAdvice, ThrowsAdvices。

### advisor

持有一个Advice，并在此基础上做另外的包装。
例如接口`PointcutAdvisor extends Advisor`，新增了获取Pointcut的功能。是将Advice和Pointcut结合的一个实体。

## 执行流程

### bean加载过程

#### spring xml配置方式

```xml
<bean id="testAdvisor" class="...TestAdvisor">

<bean id="testAOP" class="...ProxyFactoryBean">
    <property name="proxyInterfaces">xxx</property>
    <property name="target">
        <bean class="...TestTarget">
    </property>
    <property name="interceptorNames">
        <list>
            <value>testAdvisor</value>
        </list>
    </property>
</bean>

```

加载bean时，会先加载一个beanName是&testAOP的ProxyFactoryBean。加载beanName是testAOP的bean时，根据beanName拿到已经加载过的ProxyFactoryBean，从这个FactoryBean中生成一个bean。

1. 根据配置的intercetporNames加载advisors。
2. 根据是否是接口，判断用jdk还是cglib，创建一个AopProxy。这个AopProxy是具体生成代理类的。
3. 使用AopProxy生成一个代理类。

#### 基于注解配置方式

`<aop:aspectj-autoproxy/>`

1. 实例化bean时会调用AbstractAutowireCapableBeanFactory.initializeBean，过程中会调用applyBeanPostProcessorAfterInitialization
2. 会有一系列PostProcessor，其中包含了各种AutoProxyCreator。aspectj是AnnotationAwareAspectJAutoProxyCreator，但具体的process还是在父类AbstractAutoProxyCreator中。
3. AbstractAutoProxyCreator的createProxy生成一个代理类并返回。具体实现是使用一个ProxyFactory。
4. ProxyFactory.getProxy执行过程如下：
    1. createAopProxy创建一个AopProxy。会根据是否有接口等，采用jdk或是cglib。
    2. 调用AopProxy的getProxy，使用jdk或者cglib生成proxy。

### 代理执行过程

#### jdk方式

1. 走JdkDynamicAopProxy，这个实现了jdk代理的InvocationHandler。
2. 将配置的Advisor加入到interceptors chain中。如果是PointcutAdvisor，会判断当前类(getClassFilter)和方法(getMethodMatcher)是否符合切面条件。
3. 生成一个RelfectiveMethodInvocation，并执行proceed。这里proceed是递归执行，每次执行，本地的interceptor位置为递增。
4. 执行proceed之前，会执行满足条件的interceptor。此处调用了我们实现的Advice具体方法。ThrowsAdviceInterceptor比较特殊，会根据advice中方法参数的具体异常类型进行匹配，如果抛出的异常不是Advice中接收的异常，则不会执行。异常的匹配规则是从抛出异常的子类依次往父类方向找。
5. proceed执行完，拿到retVal。此处spring做了个特殊case，如果一个方法的返回类型是被代理类，则会将retVal设置成代理proxy。这样可以一定程度上解决返回this后代理失效的问题。

#### cglib

todo

### before after around afterReturning afterThrowing 执行顺序

我本地测试结果：around before -> before -> invoke -> around after -> after -> afterReturning

***新发现：在一个aspect内部，确实是按上面顺序执行，但是有多个aspect，顺序就不定了。但是before类的肯定还是在after类的先执行***

原理？？

#### aop的加载

获取到所有Advisor后，会排序。org.springframework.aop.framework.autoproxy.AbstractAdvisorAutoProxyCreator#sortAdvisors。

如果没有配置order，会按照内部规则排序。使用AspectJPrecedenceComparator。

1. 首先使用AnnotationAwareOrderComparator排序。主要是获取不同Advisor上的order/priority注解（可以是class、method、AnnotatedElement？）没有配置order时，比较结果都是0。（ExposeInvocationInterceptor比较特殊，用来在整个链路上暴露当前执行的MethodInvocation。这个的order是Interger.MIN）
2. 判断是否在一个aspect内。如果是，判断内部优先级。主要参考两个指标，一个是是否是after advice，另一个是AspectDeclarationOrder。declarationOrder是apsect类中方法的排序。内部计算规则（Around.class < Before.class < After.class < AfterReturning.class < AfterThrowing.class，如果是同类型的，则会按方法名排序）。如果没有advice是after类型，则declarationOrder小的先于大的；如果有一个advice是after类型，则declarationOrder大的先于小的。所以排序后around先于before，after先于around，afterReturing先于after。

***上面第2步提到了判断是否在一个aspect内。如果不是，会直接返回OrderComparator的结果。如果相等，那就看读入的顺序了。***

#### 使用@Order指定顺序

经测试，@Order只有加在class上才会生效，也就是说能指定不同aspect的执行顺序。同一个class内的方法上加了也没用，还是会按上面的顺序执行。
但是@Order写了对method生效，而且也有从method上找Order注解的代码。哪个环节出问题了？
根据spring官方文档，@Order在aop中，可以加在class上，也可以加在@Bean的method上。另外，也可以用在EventListener上，控制事件回调顺序。

#### 执行顺序

例子：aop的执行会走到ReflectiveMethodInvocation，成员interceptorsAndDynamicMethodMatchers = [ExposeInvocationInterceptor, AfterReturnadviceInteceptor, AspectJAfterAdviceInterceptor, AspectJAroundAdviceInterceptor, MethodBeforeAdviceIntecetptor]

为什么执行顺序是 around before -> before -> invoke -> around after -> after -> afterReturning ？
interceptor的遍历顺序和上述list一致，但是每个interceptor的动作不一样。

1. ExposeInvocationInterceptor。将MethodInvocation放入ThreadLocal，然后继续执行 im.proceed()
2. AfterReturnadviceInteceptor。先继续执行 im.proceed()，再执行afterReturn方法。
3. AspectJAfterAdviceInterceptor。先继续执行 im.proceed()，再在finally中执行after方法。
4. AspectJAroundAdviceInterceptor。先是一系列拼参数的动作，再直接调用around方法。around中有joinPoint.proceed();这里会继续执行interceptor链。
5. MethodBeforeAdviceIntecetptor。先执行before，在继续执行 im.proceed()。
6. 链执行完毕，开始反向执行。AspectJAroundAdviceInterceptor中proceed后面代码。
7. AspectJAfterAdviceInterceptor。
8. AfterReturnadviceInteceptor。

## AopContext

### 怎么实现在被代理类中currentProxy()方法返回有值，而在外部调用currentProxy()返回null？

如下代码

```java
// 被代理类

//调用test1时，内部调用test2，此时aop在test1和test2上都能工作。
public class TestTarget implements Test{

    public void test1() {
        System.out.println("test1");
        Test proxy = (Test)AopContext.currentProxy();
        test2();
    }

    public void test2() {
        System.out.println("test2");
    }
}

//当外部尝试获取AopContext.currentProxy()时为null
@Test
public void test() {
    testTarget.test1();
    // proxy 为null
    Test proxy = (Test)AopContext.currentProxy();
}
```

#### 原因

aop的ReflectiveMethodInvocation执行过程中，有如下流程：

1. 如果exposeProx==true, 执行 oldProxy = AopContext.setProxy(currentProxy);
2. 执行proceed()方法;
3. 执行AopContext.setProxy(oldProxy)。

对第一个执行的Proxy来说得到的oldProxy是null，而proceed是递归执行的。所以当整个代理过程执行完时，AopContext.setProxy(oldProxy)中的oldProxy仍然是null。

## jdk动态代理原理

java.lang.reflect.Proxy#newProxyInstance

1. 根据传入的interface[]，生成一个实现了class二进制文件。这个新class实现了所有传入的inertface。
2. 调用新class的构造器，入参是InvocationHandler。

## spring aop的新发现

在不同版本的spring中，before around after afterReturning 执行顺序不同。

### 不同版本执行顺序

5.2.8 执行顺序

@Around-before
@Before
user1 calling
@AfterReturning
@After
@Around-after

5.1.2 执行顺序

@Around-before
@Before
user1 calling
@Around-after
@After
@AfterReturning

### 内部排序

首先是注解类型的排序。

5.1.2   Around.class, Before.class, After.class, AfterReturning.class
5.2.8   Around.class, Before.class, After.class, AfterReturning.class

两个版本一致。其次，org.springframework.aop.framework.autoproxy.AbstractAdvisorAutoProxyCreator#findEligibleAdvisors
方法中又进行了一次排序

5.1.2  logResult(AfterReturning), logCall(After), logTime(Around), logParam(Before) 和排序不一致
5.2.8  logTime(Around), logParam(Before), logCall(After), logResult(AfterReturning) 和排序一致

org.springframework.aop.aspectj.autoproxy.AspectJAwareAdvisorAutoProxyCreator#sortAdvisors中会排序

5.2.8  org.aspectj.util.PartialOrder.SortObject#addDirectedLinks   before.compare(around) == 0
5.1.2  org.aspectj.util.PartialOrder.SortObject#addDirectedLinks   before.compare(around) == 1

org.springframework.aop.aspectj.autoproxy.AspectJPrecedenceComparator#comparePrecedenceWithinAspect 会根据是否是after类型和declaredOrder进行再排序。

在5.1.2版本中，declaredOrder会按 Around.class, Before.class, After.class, AfterReturning.class, AfterThrowing.class 顺序赋值，但是在5.2.8中赋值为0。

具体原因如下：

// Prior to Spring Framework 5.2.7, advisors.size() was supplied as the declarationOrderInAspect
// to getAdvisor(...) to represent the "current position" in the declared methods list.
// However, since Java 7 the "current position" is not valid since the JDK no longer
// returns declared methods in the order in which they are declared in the source code.
// Thus, we now hard code the declarationOrderInAspect to 0 for all advice methods
// discovered via reflection in order to support reliable advice ordering across JVM launches.
// Specifically, a value of 0 aligns with the default value used in
// AspectJPrecedenceComparator.getAspectDeclarationOrder(Advisor).

大概意思是由于java7不再按照代码顺序返回declared methods，所以spring为了提高在不同jvm下的可靠性，将declaredOrder赋值为0。

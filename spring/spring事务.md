# 事务

## bean加载过程

### 基于注解

1. org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory#doCreateBean
2. populateBean后会调用org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory#initializeBean
3. initializeBean中调用了org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory#applyBeanPostProcessorsAfterInitialization
4. BeanPostProcessors中包含了一个`InfrastructureAdvisorAutoProxyCreator`，它会生成一个proxy。
5. 调用postProcessAfterInitialization，实际执行的是父类AbstractAutoProxyCreator的方法。首先会获取这个class可用的interceptor，这里包含了一个BeanFactoryTransactionAttributeSourceAdvisor。
6. 使用ProxyFactory.getProxy创建一个proxy。[怎么创建一个proxy](aop.md#基于注解配置方式)
7. 此时bean已经被替换成cglib或者jdk代理类了。例如TestDbService$$EnhancerBySpringCGLIB$$c44f701e

### BeanFactoryTransactionAttributeSourceAdvisor

有两个重要成员 TransactionAttributeSourcePointcut 和 TransactionAttributeSource。 PointCut判断是否match时，使用的标准来自source。

## 事务执行过程

用的是注解 + cglib

1. 执行时走的是代理类，CglibAopProxy.DynamicAdvisedInterceptor#intercept。
2. 执行到TransactionInterceptor，并调用父类的TransactionAspectSupport.invokeWithinTransaction
3. invokeWithinTransaction 首先会获取一些配置信息和一个PlatformTransactionManager，接着开始创建一个事务，事务具体的创建是在PlatformTransactionManager中进行的。
4. PlatformTransactionManager.getTransaction执行如下：
    1. 使用doGetTransaction创建一个transaction实例，并尝试从TransactionSynchronizationManager中获取已有的ConnectionHolder。如果有，则放到自己的transaction实例中。
    2. 如果入参的TransactionDefinition是null，则设置为默认的。
    3. 判断是否已有事务。主要通过ConnectionHolder是否为null以及ConnectionHolder.isTransactionActive判断。如果已有事务，根据不同的传播级别进行处理。特别是事务的挂起操作，主要是把TransactionSynchronizationManager中的状态值保存到一个SuspendedResourcesHolder中，并清空manager中的值。
    4. 没有事务，也会根据不同的传播级别进行处理。能继续进行的话，会调用doBegin，主要做了这些事：从datasource获取一个connection并放到connectionHolder；设置connectionHolder的一些状态，比如SynchronizedWithTransaction=true，TransactionActive=true；关闭connection的autocommit；如果是一个新的connectionHolder，还会bindResource，主要是将datasource绑定到threadlocal中。
5. 执行实际类的方法。里面的一些db操作，暂时不说。
6. 执行完后，回到TransactionAspectSupport#invokeWithinTransaction中，如果有异常，执行completeTransactionAfterThrowing，否则执行commitTransactionAfterReturning。
7. completeTransactionAfterThrowing执行过程：根据RuleBasedTransactionAttribute判断异常是否需要回滚；如果需要，使用TransactionManager进行rollback，也是拿到connectionHolder中的connection进行回滚。
8. commitTransactionAfterReturning执行过程：从DefaultTransactionStatus中获取DataSourceTransactionObject，再从DataSourceTransactionObject中获取ConnectionHolder，再从ConnectionHolder中获取Connection，调用commit。

## 传播级别

PROPAGATION_REQUIRED 没有事务新开一个，有事务在事务中执行。
PROPAGATION_SUPPORTS 有事务在事务中执行，没有则非事务执行。
PROPAGATION_MANDATORY 有事务在事务中执行，没有则异常。
PROPAGATION_REQUIRES_NEW 没有事务新开一个，有事务将老事务挂起并新建一个。
PROPAGATION_NOT_SUPPORTED 有事务将老事务挂起，以非事务方式执行。
PROPAGATION_NEVER 非事务方式执行，有事务则异常。
PROPAGATION_NESTED 没有事务新开一个，有事务则嵌套执行。并不是所有事务管理器都支持嵌套。

### REQUIRES_NEW和NESTED的区别

#### 隔离级别

REQUIRES_NEW 使用单独的隔离级别。
NESTED 沿用之前事务的隔离级别。

#### 锁

REQUIRES_NEW 需要重新获取锁。
NESTED 沿用之前事务保持的锁。

下面的代码展示了锁的获取情况。如果用NESTED，可以正常执行；如果用REQUIRES_NEW，则新事务会一直等待锁直到超时，而之前的事务被挂起，类似于死锁。

```java
@Override
@Transactional(rollbackFor = RuntimeException.class)
public void deleteTwiceWithNestedTransaction(String name) {
    jobDao.deleteByName(name);
    // 使用AopContext需要
    ((JobServiceImpl) AopContext.currentProxy()).deleteTwiceWithNestedTransactionInner(name);
}

@Transactional(rollbackFor = RuntimeException.class, propagation = Propagation.NESTED)
public void deleteTwiceWithNestedTransactionInner(String name) {
    jobDao.deleteByName(name);
}
```

#### 事务的提交与回滚

REQUIRES_NEW 完全是独立的提交和回滚。外部事务的提交和回滚不会影响内部。
NESTED 只有等到外部事务提交，才会提交；内部事务回滚只会回到savepoint，这个是创建nested事务时设置的，而外部事务的回滚，会使内部事务一起回滚。

### 事务中怎么保证始终用的是一个connection

以JdbcTemplate的execute为入口。

1. 获取连接 `Connection con = DataSourceUtils.getConnection(getDataSource());`
2. DataSourceUtils.getConnection，如果开启了事务，会将connection和线程绑定。
3. 尝试从`TransactionSynchronizationManager.getResource(dataSource)`获取一个ConnectionHolder。如果为空，则new一个holder并绑定到当前线程。
4. getResource具体执行是从一个`ThreadLocal<Map<Object, Object>> resources`中获取，并用生成的key在map中找ConnectionHolder。

另外在事务的TransactionInterceptor的doBegin方法中也会进行上述类似的操作。（常用的mybatis，实际没有用到JdbcTemplate。目前还是以这个doBegin为准吧）
mybatis的SqlSessionTemplate从TransactionSynchronizationManager中获取的是SqlSession而不是connection。
如果没有开启事务，不同sql执行时，使用的是不同的sqlSession。

### 事务方法调用非事务方法，为何非事务方法也在事务内部

外部一个事务方法，调用另一个service的非事务方法，内部方法也会被事务包住。当外部回滚时，内部sql也会回滚。

具体原因如下（这里默认都是使用了mybatis）：

1. 由于没有事务内部方法调用时，会直接作为一个普通bean执行dao层方法。
2. dao层sql，具体执行时是MapperProxy，最终会调用到`org.mybatis.spring.SqlSessionTemplate.SqlSessionInterceptor#invoke`，而完成事务判断就是在这个方法中。
3. invoke调用`getSqlSession`方法获取一个sqlSession，此时有两种情况。
   1. 外层方法中已经执行过sql。`TransactionSynchronizationManager.getResource`用的key是`SqlSessionFactory`，由于外层已经执行过sql，此时获取resource不为空，直接拿到的就是正在使用的`SqlSessionHolder`，从而也就拿到了正在使用的connection。
   2. 外层方法没有执行过sql。此时内层获取的resource为空。是其实外层已经打开一个connection，内外是怎么对应的呢？当拿到的sqlSessionHolder是空时，会`sessionFactory.openSession`新建一个，此时包含的connection是null。在executor实际执行sql时，会调用`SpringManagedTransaction.getConnection`，此时会用datasource作为key，在TransactionSynchronizationManager中获取到已经存在的ConnectionHolder，从而拿到其中的connection。

sqlSession和connection的对应路径如下：

`DefaultSqlSession`->`CachingExecutor`->`SimpleExecutor`->`SpringManagedTransaction`->`connection`

*一个debug的小插曲，在idea调试中，每次测试上面的3.2，拿到的resouce总是不为空，但是其中的connection是空。关闭idea的debug选项：Enable toString object view之后就正常了。*

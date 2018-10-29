# 事务

## 传播级别

PROPAGATION_REQUIRED 没有事务新开一个，有事务在事务中执行。
PROPAGATION_SUPPORTS 有事务在事务中执行，没有则非事务执行。
PROPAGATION_MANDATORY 有事务在事务中执行，没有则异常。
PROPAGATION_REQUIRES_NEW 没有事务新开一个，有事务将老事务挂起并新建一个。
PROPAGATION_NOT_SUPPORTED 有事务将老事务挂起，以非事务方式执行。
PROPAGATION_NEVER 非事务方式执行，有事务则异常。
PROPAGATION_NESTED 没有事务新开一个，有事务则嵌套执行。并不是所有事务管理器都支持嵌套。

### 事务方法调用非事务方法，为何非事务方法也在事务内部？

获取事务是在org.springframework.transaction.support.AbstractPlatformTransactionManager#getTransaction。如果参数TransactionDefinition是null，会生成默认的TransactionDefinition，这个默认的传播级别是required。

### 事务中怎么保证始终用的是一个connection

以JdbcTemplate的execute为入口。

1. 获取连接 `Connection con = DataSourceUtils.getConnection(getDataSource());`
2. DataSourceUtils.getConnection，如果开启了事务，会将connection和线程绑定。
3. 尝试从`TransactionSynchronizationManager.getResource(dataSource)`获取一个ConnectionHolder。如果为空，则new一个holder并绑定到当前线程。
4. getResource具体执行是从一个`ThreadLocal<Map<Object, Object>> resources`中获取，并用生成的key在map中找ConnectionHolder。

另外在事务的TransactionInterceptor的doBegin方法中也会进行上述类似的操作。（常用的mybatis，实际没有用到JdbcTemplate。目前还是以这个doBegin为准吧）
mybatis的SqlSessionTemplate从TransactionSynchronizationManager中获取的是SqlSession而不是connection。
如果没有开启事务，不同sql执行时，使用的是不同的sqlSession。
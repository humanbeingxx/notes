# mybatis

## 执行流程

1. cglib proxy invoke。
2. org.apache.ibatis.binding.MapperProxy#invoke。
3. 用MapperMethod启动SqlSession的执行。根据不同的sql类型，调用sqlSessionProxy的不同方法。
4. 一般用SqlSessonTemplate。管理了sqlsession的open、commit、close等生命周期，还能结合spring的事务。
5. 实际执行的是默认实现DefaultSqlSession。内部使用Executor。会生成一个Statement一般是PrepareStatement。
6. 后面就是PrepareStatement内部的执行了。属于mysql层面。
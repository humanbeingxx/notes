# quartz

## 入门

### 简单使用

boot2已经默认配置好了quartz的基础配置，可以容易的单机本地执行。

简单的手动配置方法：

```java

// 定义一个job类
public class StatisticsCronJob implements QuartzJobBean {

    @Override
    protected void executeInternal(JobExecutionContext context) {
        // do work
    }
}

// 生成JobDetail

JobDetail jobDetail = JobBuilder.newJob(job.getClass())
                    .withIdentity(jobKey)
                    // 不设置为durable，则不能单独addJob
                    .storeDurably(true)
                    .build();

// 存储到定时任务中
// 这里的scheduler是一个Scheduler类型的bean，spring已经默认生成了一个
scheduler.addJob(jobDetail, true);

// 定义一个trigger，开始调度
CronScheduleBuilder scheduleBuilder = CronScheduleBuilder
                .cronSchedule("0/5 * * * * ?")
                .withMisfireHandlingInstructionDoNothing();

CronTrigger cronTrigger = TriggerBuilder.newTrigger()
        .forJob("job_id")
        .withIdentity("trigger_id")
        .withSchedule(scheduleBuilder)
        .startNow()
        .build();

scheduler.scheduleJob(cronTrigger);
```

上面一顿操作后，定时任务就开始执行了。

### 多台机器执行（基于mysql）

需要将job、trigger等定时任务基础单位存到mysql中。

yml文件中简单配置如下

```yaml
spring:
  quartz:
    job-store-type: jdbc
```

默认是用的datasource是项目中已经定义好的datasource，如果需要和业务库隔离，需要单独配置datasource。

配置好后，addJob会在QRTZ_JOB_DETAILS表中添加数据。scheduleJob会在QRTZ_TRIGGERS中添加数据，如果是cron，则也会在QRTZ_CRON_TRIGGERS中添加数据。开始执行的trigger，会在QRTZ_FIRED_TRIGGERS写入数据。

#### 防止并发执行

使用jdbc持久化后，多台机器之间获取trigger时会加锁，不会出现同时获取trigger并执行的情况。
但是当任务在下一次调度时还未完成时会出现下面两个问题：本地会并发执行；多台机器会并发执行。

解决第一个问题，在job类加上@DisallowConcurrentExecution。
解决第二个问题，还需要让scheduler成为cluster模式，加上配置

```yaml
spring:
  quartz:
    job-store-type: jdbc
    properties:
      org.quartz.scheduler.instanceName: clusteredScheduler
      org.quartz.scheduler.instanceId: AUTO
      org.quartz.scheduler.autoId: true
      org.quartz.jobStore.class: org.quartz.impl.jdbcjobstore.JobStoreTX
      org.quartz.jobStore.isClustered: true
```

#### 陷入了blocked状态

在做unittest时，由于中断了测试，导致一个trigger的状态成了BLOCKED。
想把该任务状态重置，resume无效，reschedule无效，resetTriggerFromErrorState无效。

同时有AB两个进程在进行调度时，如果A挂了，B会侦测到失败信息并重新接手该任务。

```log
2018-11-19 12:01:06.136 [QuartzScheduler_clusteredScheduler-fenglinhuoshandeMacBook-Pro.local1542600033236_ClusterManager] INFO  o.s.s.q.LocalDataSourceJobStore - ClusterManager: detected 1 failed or restarted instances.
2018-11-19 12:01:06.136 [QuartzScheduler_clusteredScheduler-fenglinhuoshandeMacBook-Pro.local1542600033236_ClusterManager] INFO  o.s.s.q.LocalDataSourceJobStore - ClusterManager: Scanning for instance "fenglinhuoshandeMacBook-Pro.local1542599985123"'s failed in-progress jobs.
2018-11-19 12:01:06.142 [QuartzScheduler_clusteredScheduler-fenglinhuoshandeMacBook-Pro.local1542600033236_ClusterManager] INFO  o.s.s.q.LocalDataSourceJobStore - ClusterManager: ......Cleaned-up 1 other failed job(s).
```

##### 各种状态的解释

- WAITING = the normal state of a trigger, waiting for its fire time to arrive and be acquired for firing by a scheduler.

- PAUSED = means that one of the scheduler.pauseXXX() methods was used. The trigger is not eligible for being fired until it is resumed.

- ACQUIRED = a scheduler node has identified this trigger as the next trigger it will fire - may still be waiting for its fire time to arrive. After it fires the trigger will be updated (per its repeat settings, if any) and placed back into the WAITING state (or be deleted if it does not repeat again).

- BLOCKED = the trigger is prevented from being fired because it relates to a StatefulJob that is already executing. When the statefuljob completes its execution, all triggers relating to that job will return to the WAITING state.

#### 建表语句

在quartz的jar包中。如下

```sql
-- innodb版本

DROP TABLE IF EXISTS QRTZ_FIRED_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_PAUSED_TRIGGER_GRPS;
DROP TABLE IF EXISTS QRTZ_SCHEDULER_STATE;
DROP TABLE IF EXISTS QRTZ_LOCKS;
DROP TABLE IF EXISTS QRTZ_SIMPLE_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_SIMPROP_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_CRON_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_BLOB_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_JOB_DETAILS;
DROP TABLE IF EXISTS QRTZ_CALENDARS;

CREATE TABLE QRTZ_JOB_DETAILS(
SCHED_NAME VARCHAR(120) NOT NULL,
JOB_NAME VARCHAR(190) NOT NULL,
JOB_GROUP VARCHAR(190) NOT NULL,
DESCRIPTION VARCHAR(250) NULL,
JOB_CLASS_NAME VARCHAR(250) NOT NULL,
IS_DURABLE VARCHAR(1) NOT NULL,
IS_NONCONCURRENT VARCHAR(1) NOT NULL,
IS_UPDATE_DATA VARCHAR(1) NOT NULL,
REQUESTS_RECOVERY VARCHAR(1) NOT NULL,
JOB_DATA BLOB NULL,
PRIMARY KEY (SCHED_NAME,JOB_NAME,JOB_GROUP))
ENGINE=InnoDB;

CREATE TABLE QRTZ_TRIGGERS (
SCHED_NAME VARCHAR(120) NOT NULL,
TRIGGER_NAME VARCHAR(190) NOT NULL,
TRIGGER_GROUP VARCHAR(190) NOT NULL,
JOB_NAME VARCHAR(190) NOT NULL,
JOB_GROUP VARCHAR(190) NOT NULL,
DESCRIPTION VARCHAR(250) NULL,
NEXT_FIRE_TIME BIGINT(13) NULL,
PREV_FIRE_TIME BIGINT(13) NULL,
PRIORITY INTEGER NULL,
TRIGGER_STATE VARCHAR(16) NOT NULL,
TRIGGER_TYPE VARCHAR(8) NOT NULL,
START_TIME BIGINT(13) NOT NULL,
END_TIME BIGINT(13) NULL,
CALENDAR_NAME VARCHAR(190) NULL,
MISFIRE_INSTR SMALLINT(2) NULL,
JOB_DATA BLOB NULL,
PRIMARY KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP),
FOREIGN KEY (SCHED_NAME,JOB_NAME,JOB_GROUP)
REFERENCES QRTZ_JOB_DETAILS(SCHED_NAME,JOB_NAME,JOB_GROUP))
ENGINE=InnoDB;

CREATE TABLE QRTZ_SIMPLE_TRIGGERS (
SCHED_NAME VARCHAR(120) NOT NULL,
TRIGGER_NAME VARCHAR(190) NOT NULL,
TRIGGER_GROUP VARCHAR(190) NOT NULL,
REPEAT_COUNT BIGINT(7) NOT NULL,
REPEAT_INTERVAL BIGINT(12) NOT NULL,
TIMES_TRIGGERED BIGINT(10) NOT NULL,
PRIMARY KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP),
FOREIGN KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP)
REFERENCES QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP))
ENGINE=InnoDB;

CREATE TABLE QRTZ_CRON_TRIGGERS (
SCHED_NAME VARCHAR(120) NOT NULL,
TRIGGER_NAME VARCHAR(190) NOT NULL,
TRIGGER_GROUP VARCHAR(190) NOT NULL,
CRON_EXPRESSION VARCHAR(120) NOT NULL,
TIME_ZONE_ID VARCHAR(80),
PRIMARY KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP),
FOREIGN KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP)
REFERENCES QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP))
ENGINE=InnoDB;

CREATE TABLE QRTZ_SIMPROP_TRIGGERS
  (
    SCHED_NAME VARCHAR(120) NOT NULL,
    TRIGGER_NAME VARCHAR(190) NOT NULL,
    TRIGGER_GROUP VARCHAR(190) NOT NULL,
    STR_PROP_1 VARCHAR(512) NULL,
    STR_PROP_2 VARCHAR(512) NULL,
    STR_PROP_3 VARCHAR(512) NULL,
    INT_PROP_1 INT NULL,
    INT_PROP_2 INT NULL,
    LONG_PROP_1 BIGINT NULL,
    LONG_PROP_2 BIGINT NULL,
    DEC_PROP_1 NUMERIC(13,4) NULL,
    DEC_PROP_2 NUMERIC(13,4) NULL,
    BOOL_PROP_1 VARCHAR(1) NULL,
    BOOL_PROP_2 VARCHAR(1) NULL,
    PRIMARY KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP),
    FOREIGN KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP)
    REFERENCES QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP))
ENGINE=InnoDB;

CREATE TABLE QRTZ_BLOB_TRIGGERS (
SCHED_NAME VARCHAR(120) NOT NULL,
TRIGGER_NAME VARCHAR(190) NOT NULL,
TRIGGER_GROUP VARCHAR(190) NOT NULL,
BLOB_DATA BLOB NULL,
PRIMARY KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP),
INDEX (SCHED_NAME,TRIGGER_NAME, TRIGGER_GROUP),
FOREIGN KEY (SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP)
REFERENCES QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP))
ENGINE=InnoDB;

CREATE TABLE QRTZ_CALENDARS (
SCHED_NAME VARCHAR(120) NOT NULL,
CALENDAR_NAME VARCHAR(190) NOT NULL,
CALENDAR BLOB NOT NULL,
PRIMARY KEY (SCHED_NAME,CALENDAR_NAME))
ENGINE=InnoDB;

CREATE TABLE QRTZ_PAUSED_TRIGGER_GRPS (
SCHED_NAME VARCHAR(120) NOT NULL,
TRIGGER_GROUP VARCHAR(190) NOT NULL,
PRIMARY KEY (SCHED_NAME,TRIGGER_GROUP))
ENGINE=InnoDB;

CREATE TABLE QRTZ_FIRED_TRIGGERS (
SCHED_NAME VARCHAR(120) NOT NULL,
ENTRY_ID VARCHAR(95) NOT NULL,
TRIGGER_NAME VARCHAR(190) NOT NULL,
TRIGGER_GROUP VARCHAR(190) NOT NULL,
INSTANCE_NAME VARCHAR(190) NOT NULL,
FIRED_TIME BIGINT(13) NOT NULL,
SCHED_TIME BIGINT(13) NOT NULL,
PRIORITY INTEGER NOT NULL,
STATE VARCHAR(16) NOT NULL,
JOB_NAME VARCHAR(190) NULL,
JOB_GROUP VARCHAR(190) NULL,
IS_NONCONCURRENT VARCHAR(1) NULL,
REQUESTS_RECOVERY VARCHAR(1) NULL,
PRIMARY KEY (SCHED_NAME,ENTRY_ID))
ENGINE=InnoDB;

CREATE TABLE QRTZ_SCHEDULER_STATE (
SCHED_NAME VARCHAR(120) NOT NULL,
INSTANCE_NAME VARCHAR(190) NOT NULL,
LAST_CHECKIN_TIME BIGINT(13) NOT NULL,
CHECKIN_INTERVAL BIGINT(13) NOT NULL,
PRIMARY KEY (SCHED_NAME,INSTANCE_NAME))
ENGINE=InnoDB;

CREATE TABLE QRTZ_LOCKS (
SCHED_NAME VARCHAR(120) NOT NULL,
LOCK_NAME VARCHAR(40) NOT NULL,
PRIMARY KEY (SCHED_NAME,LOCK_NAME))
ENGINE=InnoDB;

CREATE INDEX IDX_QRTZ_J_REQ_RECOVERY ON QRTZ_JOB_DETAILS(SCHED_NAME,REQUESTS_RECOVERY);
CREATE INDEX IDX_QRTZ_J_GRP ON QRTZ_JOB_DETAILS(SCHED_NAME,JOB_GROUP);

CREATE INDEX IDX_QRTZ_T_J ON QRTZ_TRIGGERS(SCHED_NAME,JOB_NAME,JOB_GROUP);
CREATE INDEX IDX_QRTZ_T_JG ON QRTZ_TRIGGERS(SCHED_NAME,JOB_GROUP);
CREATE INDEX IDX_QRTZ_T_C ON QRTZ_TRIGGERS(SCHED_NAME,CALENDAR_NAME);
CREATE INDEX IDX_QRTZ_T_G ON QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_GROUP);
CREATE INDEX IDX_QRTZ_T_STATE ON QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_STATE);
CREATE INDEX IDX_QRTZ_T_N_STATE ON QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP,TRIGGER_STATE);
CREATE INDEX IDX_QRTZ_T_N_G_STATE ON QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_GROUP,TRIGGER_STATE);
CREATE INDEX IDX_QRTZ_T_NEXT_FIRE_TIME ON QRTZ_TRIGGERS(SCHED_NAME,NEXT_FIRE_TIME);
CREATE INDEX IDX_QRTZ_T_NFT_ST ON QRTZ_TRIGGERS(SCHED_NAME,TRIGGER_STATE,NEXT_FIRE_TIME);
CREATE INDEX IDX_QRTZ_T_NFT_MISFIRE ON QRTZ_TRIGGERS(SCHED_NAME,MISFIRE_INSTR,NEXT_FIRE_TIME);
CREATE INDEX IDX_QRTZ_T_NFT_ST_MISFIRE ON QRTZ_TRIGGERS(SCHED_NAME,MISFIRE_INSTR,NEXT_FIRE_TIME,TRIGGER_STATE);
CREATE INDEX IDX_QRTZ_T_NFT_ST_MISFIRE_GRP ON QRTZ_TRIGGERS(SCHED_NAME,MISFIRE_INSTR,NEXT_FIRE_TIME,TRIGGER_GROUP,TRIGGER_STATE);

CREATE INDEX IDX_QRTZ_FT_TRIG_INST_NAME ON QRTZ_FIRED_TRIGGERS(SCHED_NAME,INSTANCE_NAME);
CREATE INDEX IDX_QRTZ_FT_INST_JOB_REQ_RCVRY ON QRTZ_FIRED_TRIGGERS(SCHED_NAME,INSTANCE_NAME,REQUESTS_RECOVERY);
CREATE INDEX IDX_QRTZ_FT_J_G ON QRTZ_FIRED_TRIGGERS(SCHED_NAME,JOB_NAME,JOB_GROUP);
CREATE INDEX IDX_QRTZ_FT_JG ON QRTZ_FIRED_TRIGGERS(SCHED_NAME,JOB_GROUP);
CREATE INDEX IDX_QRTZ_FT_T_G ON QRTZ_FIRED_TRIGGERS(SCHED_NAME,TRIGGER_NAME,TRIGGER_GROUP);
CREATE INDEX IDX_QRTZ_FT_TG ON QRTZ_FIRED_TRIGGERS(SCHED_NAME,TRIGGER_GROUP);

commit;
```

## quartz工作原理

### 基础组件

#### Job && JobDetail

Job定义了一个任务具体执行逻辑。
JobDetail是quartz中任务的定义单位，包含了Job的class和执行任务时需要的其他信息。
有点像Runnable、Thread、ThreadPool的关系。

##### JobDataMap

可以在JobDataMap中存储运行job需要的参数。JobDetai和Trigger中都可以使用JobDataMap，方法是usingJobData。
获取方法：

1. 如果Job类有key对应的set方法，比如setJobName，则会自动调用set方法，注入Job类中。
2. 从JobExecutionContext中直接获取JobDataMap。这个map是JobDetai和Trigger的merge结果，且相同key时后者会覆盖前者。

##### @DisallowConcurrentExecution

注解可以防止job并发执行，但是需要注意的是，虽然注解是加在类上，但是生效的维度是JobDetail。
如果在一个Job上创建了两个JobDetail A和B，name只能防止A和A并发，不能防止A和B并发。

##### @PersistJobDataAfterExecution

执行完成execute后，将jobDetail的JobDataMap写入QRTZ_JOB_DETAILS表的JOB_DATA字段。

> 我在使用时，能正常写入，但是项目启动后的第一次读取总是空。原因是我在启动时，监听了spring context加载完成后新建了JobDetail覆盖了表中的job，导致DATA字段被覆盖为空。解决方案是读取原JobDetail的data，放到new JobDetail中。

##### JobDetail的isDurable

如果isDurable = false，则不能单独addJob，且当job没有trigger关联时，会自动删除。

### 问题 && 新发现

#### job必须有无参构造器？

其实不然。在spring环境中，使用的JobFactory是SpringBeanJobFactory，当构造器中有参数时，会用autowire的方式去spring context获取bean来填充。
所以我之前的版本构造器用了ApplicationContext能通过。

#### 项目启动时会同时执行同一个任务

目前看原因是misFire，也就是说在项目关闭期间未执行的任务会再次执行。但问题是，我配置的misFire策略是MISFIRE_INSTRUCTION_DO_NOTHING，这样应该不会重复执行。trigger表中的type是cron，misfire_instr也是2。
还有一种SimpleTrigger实现，它里面的code2对应的是MISFIRE_INSTRUCTION_RESCHEDULE_NOW_WITH_EXISTING_REPEAT_COUNT，现象和这个策略完全一致，why?
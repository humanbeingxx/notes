# sqoop

## mysql -> hive

sqoop import \
--connect jdbc:mysql://localhost:3306/test \
--username test \
--password test1234 \
--table tb_job \
--where "salary > 1300" \
--fields-terminated-by "\t" \
--m 1 \
--hive-import \
--create-hive-table  \
--delete-target-dir \
--hive-database hive_test \
--hive-table tb_job

## hive -> mysql

sqoop export \
--connect jdbc:mysql://localhost:3306/test \
--username test \
--password test1234 \
--table tb_job_backup \
--update-key code \
--update-mode allowinsert \
--fields-terminated-by "\t" \
--m 1 \
--export-dir hdfs://localhost:9000/user/hive/warehouse/hive_test.db/tb_job

## 中间遇到的问题

### ClassNotDefError

具体报错是 FileNotExistException: localhost:9000/User/......(本地路径)/lib/xxx.jar not exists。

解决方法是把整个sqoop拷贝到hdfs上。

### 找不到 HIVE_CONFIG_DIR

具体报错是

> Could not load org.apache.hadoop.hive.conf.HiveConf. Make sure HIVE_CONF_DIR is set correctly.

用了最简单直接的方法，将hive下的hive-common和hive-exec两个jar拷贝到sqoop本地lib和hdfs上。
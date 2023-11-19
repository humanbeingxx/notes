# 本文只针对有本地debug经验的开发。

## 查看项目是否已经开启远程debug模式

看tomcat目录的startenv.sh是否包含下面这行。
export JAVA_OPTS=$JAVA_OPTS" -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=127.0.0.1:60001 
如果没有包含，点noah服务器列表中的远程debug

## 在服务器控制台上开启socat命令

socat TCP4-LISTEN:9612,fork,range=你本地的ip/32 TCP4:127.0.0.1:60001 其中range=本地ip

## idea创建remote

host填noah机器，post就是TCP4-LISTEN:后面的port

点击debug按钮
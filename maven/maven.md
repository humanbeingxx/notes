# REVISE

## 依赖

### 传递性

runtime: 对于测试和运行时的classpath有效，对编译代码classpath无效。

A -> B -> C

下表中横抬头表示A中B的scope，纵向抬头表示B中C的scope。
表中数据表示A中A的scope。

| -        | compile | test | provider | runtime |
|----------|---------|------|----------|---------|
| compile  | compile | test | provider | runtime |
| test     | -       | -    | -        | -       |
| provider | -       | -    | -        | -       |
| runtime  | runtime | test | provider | runtime |

为什么runtime时，代码中仍然能直接使用包内的class，而provider在运行时仍然有效？shit，原因是我写的是单元测试，runtime和provider都有效。换到main中就不行了。
用runtime的好处是避免程序里意外引入了应该动态加载的资源。

#### 最短路径依赖优先。同长度路径，先声明者优先。

#### 可选依赖

```xml
<optional>true</optional>
```

效果：不会传递依赖，如果A需要用到C，必须显示依赖C。

使用场景：一个工具包囊括了mysql、pg、oracle等各种数据库，但是使用这个工具时，只能有一个特性能生效。

不推荐使用可选依赖，比较好的方案是建多个工具包。

### mvn dependency:analyze

从代码使用层面检查依赖，无法检查运行时。

### refactor

-am 构建模块以及依赖的模块
-amd 构建模块以及依赖这个模块的模块
-fr 从指定模块开始构建

## 疑问

pom中配置多个versino是什么意思？
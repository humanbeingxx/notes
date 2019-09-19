# 从零学习RESOURCE

## 问与答

### 如果指定profile和resource下有相同的文件，是什么规则？优先使用哪个？覆盖还是merge？

- 只配置一个resources文件夹，如下，则只会用local目录下的东西。

```xml
<resources>
    <resource>
        <directory>src/main/resources.local</directory>
    </resource>
</resources>
```

- 如果加上如下，则会进行文件（而不是文件内容）维度的覆盖和合并。而且是哪个resource在pom中的位置靠前，优先使用哪个

```xml
<directory>src/main/resources</directory>
```

- 如果在外层的build中配置resource，是什么规则？也是文件维度的覆盖和合并，而且优先使用build中的配置（有点违和，为什么不优先使用指定的profile），无论build和profile的位置如何。

```xml
<profiles>
    <profile>
        <id>local</id>
        <build>
            <resources>
                <resource>
                    <directory>src/main/resources.local</directory>
                </resource>
            </resources>
        </build>
    </profile>
</profiles>

<build>
    <resources>
        <resource>
            <directory>src/main/resources</directory>
        </resource>
    </resources>
</build>
```

## resource的filter

### filter = true

- 表示需要用pom中变量替换resource中文件的变量${}。
- 此时用include指定需要替换的文件或用exclude去掉不用替换的文件，默认都参与替换。
- **特别注意，用了filter会使文件覆盖规则变化**

```xml
覆盖时，使用resources中文件
<resources>
    <resource>
        <directory>src/main/resources.local</directory>
    </resource>
    <resource>
        <directory>src/main/resources</directory>
        <filtering>true</filtering>
    </resource>
</resources>
```

```xml
覆盖时，使用resources中文件
<resources>
    <resource>
        <directory>src/main/resources.local</directory>
        <filtering>true</filtering>
    </resource>
    <resource>
        <directory>src/main/resources</directory>
        <filtering>true</filtering>
    </resource>
</resources>
```

resources使用filter | local使用filter | 顺序 | 结果
---------|----------|---------|----------
不使用 | 使用 | local先 | 用local
不使用 | 使用 | local后 | 用local
使用 | 使用 | local先 | 用resources
使用 | 使用 | local后 | 用local
使用 | 不使用 | local先 | 用resources
使用 | 不使用 | local后 | 用resources

### exclude = true（默认）

- 如果include和exclude同时包含了一个文件怎么办。。。会exclude掉。
- **又要注意，include和exclude不仅用于filter，更会对是否将文件作为资源文件这个判断生效**。如下

```xml
resource中有三个文件1.properties, 2.properties, 3.properties
如何实现将三个文件都作为资源文件，且只替换2.properties？

<resources>
    <resource>
        <directory>src/main/resources</directory>
        <filtering>true</filtering>
        <includes>
            <include>2.properties</include>
        </includes>
    </resource>
    <resource>
        <directory>src/main/resources</directory>
        <excludes>
            <exclude>2.properties</exclude>
        </excludes>
    </resource>
    <resource>
        <directory>src/main/resources.local</directory>
    </resource>
</resources>
```

- 将include和exclude换换位置怎么样？include还是会生效！

### 外层build-resource中的filter

- 如果配置的文件没有冲突，可以同时生效
- 冲突时，比如build中exclude，profile中include，最终会include；如果是profile中exclude，build中exclude，还是会include

## maven resource和springboot的profile关系

maven resource插件，可以控制根据参数中的profile选择使用哪些文件。最终target文件夹中不一定会包含所有文件。
springboot不对文件动手脚，而是根据参数选用不同的文件。

如果对最终生成的文件没有特殊要求，可以直接用springboot机制。

## TODO 遇到其他阴险的问题继续补充

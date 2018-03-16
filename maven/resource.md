# RESOURCE

## resources的覆盖

```xml
<build>
    <testResources>
        <testResource>
            <directory>src/test/resources.local</directory>
        </testResource>
        <testResource>
            <directory>src/test/resources</directory>
        </testResource>
    </testResources>
</build>
```

mvn clean resources:testResources compiler:testCompile -Plocal
如果local和resources有相同文件，会使用第一个遇到的文件，不覆盖
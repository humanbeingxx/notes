# NOTES

## session

global是session级别的，一个session中修改global不会影响另一个session中global的值。

kmodule文件中如果有两个session的default=true，则使用时拿不到default session。

### StatelessSession

## rule

### methods vs rules

- 方法可以直接调用；一次执行只调用一次
- 只要插入到引擎，就会执行规则，无论数据是什么格式；不能直接调用；根据匹配的情况，一个规则可能会执行多次，或者一次不执行

### marshall？

### Inference

🌰：公安部制定规则，给什么样的人发身份证

用一个决策表表示

| RuleTableIdCard | RuleTableIdCard | RuleTableIdCard | RuleTableIdCard |
| --------------- | --------------- | --------------- | --------------- |
| -----           | condition       | condition       | action          |
| -----           | p:person        | p:person        | p:person        |
| -----           | location        | age >= $1       | issueIdCard($1) |
| -----           | select person   | select adults   | issue id card   |
| issue id card   | beijing         | 18              | p               |

问题是如果《民法》规定成年从18变成19，公安部需要同步修改规则。
这里可以使用inference将18岁的规则抽离。

- 立法部门维护年龄规则

| RuleTableAge  | RuleTableAge  | RuleTableAge   |
| ------------- | ------------- | -------------- |
| -----         | condition     | action         |
| -----         | p:person      | -----          |
| -----         | age >= $1     | insert($1)     |
| -----         | select adults | adult relation |
| issue id card | 18            | newIsAdult(p)  |

- 公安部维护身份证发放规则

| RuleTableIdCard | RuleTableIdCard | RuleTableIdCard | RuleTableIdCard |
| --------------- | --------------- | --------------- | --------------- |
| -----           | condition       | condition       | action          |
| -----           | p:person        | isAdult         | -----           |
| -----           | location        | person == $1    | issueIdCard($1) |
| -----           | select person   | select adults   | issue id card   |
| issue id card   | beijing         | p               | p               |

### TMS(Truth Maintenanace System)

- [ ] 这个章节确实没看懂

### 使用决策表

*如果同时使用决策表和drl文件，kmodule中需要指定决策表的packages，否则用drls时会报NPE*

#### 决策表的几个关键概念

总体分为两个部分，RuleSet部分和RuleTable部分。

**其实所谓的决策表，也就是直接通过语法对应，拼成了一个drl文件**

##### RuleSet区域

| 关键词     | 可用值                  | 作用（未标明的都是非必填）   |
| ---------- | ----------------------- | ---------------------------- |
| RuleSet    | 生成的drl文件的package  | 必须是第一行（必填）         |
| Sequential | true/false              | 和salience配合，决定执行顺序 |
| Import     | 同drl的                 | -                            |
| Variables  | 同drl重的globals        | -                            |
| Functions  | 定义方法，语法和drl相同 | -                            |

*注意，在RuleSet中设置的属性会影响整个package中的规则。*

##### 决策表翻译成drls BY org.drools.decisiontable.SpreadsheetCompiler

##### CONDITION

CONDITION关键词下的第一行是规则条件的“模式”。合并单元格表示，同时满足这些模式。
CONDITION关键词下的第一行可以为空，但是第二行必须能单独成为一个条件表达式。

在condition中，可以用逗号分隔参数，引用时使用$1 $2...

##### 什么是forall？在决策表中怎么用？

- [ ] 实际的含义需要等看完语法才能补充

我在excel里面写了个🌰
CONDITION中有一列是 forall(,){key2 != "$"}，翻译成drls之后是

```drl
rule "ComplicatedTables_9"
    salience 65527
    when
        $com:ComplicatedUse(key1 == "1", key2 != "a" , key2 != "A")
    then
        $com.setResult(1);
end
```

其中a和A是配置在excel条件中的，用逗号分隔  a,A

在这个例子中表示的是，key2要同时满足一个单元格中的所有条件。

#### 使用决策表遇到的问题

##### 配置了xls，怎么加载为规则？

drools会自动加载classpath下的xls作为规则，需要在xml中配置package（不配置则是默认）。

##### kmodule.xml中配置了session，为什么规则加载不进去？

和session配置中的package有关。package要写成文件所在路径。比如在resources下的rules中，则写成"rules"。
xls中的RuleSet也是package，但是和xml中的package没有关系。
RuleSet的package，是这个规则运行时所在的java package。可以直接使用该package下的class。
RuleSet可以为空，需要用到的类全部可用import加载进来。

##### 最初加载报各种异常的问题

看的UserGuide是6.x版本的，但是drools引用的是RELEASE，是7.x。不兼容。
换成6.x的drools后，ClassNotFound，原因是需要手动引入decisionTable这个依赖。

##### 决策表格式问题

下面的表是我测试用的

| CONDITION     | CONDITION     | ACTION                     |
| ------------- | ------------- | -------------------------- |
| person:Person | person:Person | person:Person              |
| age           | gender        | person.setColor("$param"); |
| 17            | male          | red                        |
| 17            | female        | pink                       |
| 19            | male          | black                      |
| 19            | female        | purle                      |

- 遇到的第一个问题：person.setColor("$param")不加分号，提示这里必须是一个boolean类型的表达式。加上分号，提示找不到@positional field。各种尝试后，发现需要将前两行的person:Person合并成一个单元格。。。**具体原理还不知道**
- 遇到的第二个问题：规则第一行不生效。xls的格式可能是固定的。age这个下面必须是注释。变成了

| age        | gender     | person.setColor("$param"); |
| ---------- | ---------- | -------------------------- |
| 必须是注释 | 必须是注释 | 必须是注释                 |
| 17         | male       | red                        |

## KieRuntime相关

### insert

*为什么用insert这个关键词？ 因为assert这个是大多数语言的关键词*

插入Working Memory时，有两种断言模式：
Identity 使用IdentityHashMap，对象比较使用 ==
Equality 使用HashMap，对象使用equal和hashcode

### update

对应excel中的modify，能通知WorkingMemory感知到fact的变化。

### query

一种是静态query，一种是动态query(LiveQuery)。
静态的只要在session中insert或者其他操作，就可以查询；动态query需要执行fireAllRules，才能生效。

### 冲突解决

默认提供两种方式：优先级和LIFO

#### 疑问

drools的冲突是怎么定义的。
我定义了一个规则，两个相同的条件，不同的action，结果都执行了。
定义了salience，确实先执行了值大的规则，但是剩下的还是会继续执行。这是应该有的行为吗？能不能只命中一条规则？只命中一条是合理的行为吗？

#### AgendaGroup && ActivationGroup

- 不显式配置AgendaGroup的rule，默认都在MAIN group中，这个group会默认放在执行栈中。
- 配置了AgendaGroup的rule，默认是没有focus的，需要代码中调用，或者配置成auto-focus。
- AgendaGroup的优先级高于salience，同group内salience生效。

同一次insert，fire多次，一个rule只会生效一次。

```java
session.insert(Person.builder().age(20).build());
session.fireAllRules();

session.getAgenda().getAgendaGroup("first").setFocus();
session.fireAllRules();

session.getAgenda().getAgendaGroup("second").setFocus();
session.fireAllRules();

这段代码，fire了3次，单每次都只有一个rule命中（由于group的控制）
```

```java

session.insert(Person.builder().age(20).build());
session.getAgenda().getAgendaGroup("first").setFocus();
session.getAgenda().getAgendaGroup("second").setFocus();

session.fireAllRules();

使用AgendaGroup时，确实是入栈操作，上面的规则，匹配顺序是second -> first -> MAIN
```

和AgendaGroup不同，同一ActivationGroup中的规则只会有一个命中。

### Event

```java
session.addEventListener(new DefaultAgendaEventListener() {
    @Override
    public void matchCreated(MatchCreatedEvent event) {
        System.out.println(event.getMatch().getRule().getName());
    }
});

session.fireAllRules(new RuleNameMatchesAgendaFilter("Test Event.*"));
```

这段代码，虽然fire的结果，是只有Test Event 这样的rule才会被匹配，但是在创建match时，其他规则如果能匹配上，事件也会发生。
也就是说，filter只是对结果做了过滤，对执行过程并没有。

- [] 会不会存在“随着规则的增多，执行效率下降”的问题？

### Propagation modes

举个🌰

drools中允许在rule中使用query

```drls
// 存在一个string和给定的int的字符串值相等
query Q (Integer i)
    String( this == i.toString() )
end

rule "propagation_mode_immediate" @Propagation(IMMEDIATE)

// 对一个int i，存在一个string和i的字符串值相等
when
    $i : Integer()
    ?Q ($i;)
then
    System.out.println("propagation_mode_immediate rule firing");
end
```

下面是使用方式

```java
session.insert(1);
session.insert("1");
session.fireAllRules();
```

在passive mode中，无法感知到后插入的"1"，因而，按照理解这个规则是不应该命中的。
但由于PHREAK算法的lazy模式，导致无法区分两个fact的插入顺序，规则会命中（调整int和string的插入顺序，仍然会命中）。

通过在rule上加@Propagation解决。
有三种模式（还是来原文吧）

<table border="1px">
    <tr>
      <th bgcolor="0099FF">key</th>
      <th bgcolor="0099FF">effect</th>
    </tr>
    <tr bgcolor="#d4e3e5" onmouseover="this.style.backgroundColor='#ffff66';" onmouseout="this.style.backgroundColor='#d4e3e5';">
      <td>IMMEDIATE</td>
      <td>the propagation is performed immediately </td>
    </tr>
<tr bgcolor="#d4e3e5" onmouseover="this.style.backgroundColor='#ffff66';" onmouseout="this.style.backgroundColor='#d4e3e5';">
      <td>EAGER</td>
      <td>the propagation is performed lazily but eagerly evaluated before scheduled evaluations</td>
    </tr>
    </tr>
<tr bgcolor="#d4e3e5" onmouseover="this.style.backgroundColor='#ffff66';" onmouseout="this.style.backgroundColor='#d4e3e5';">
      <td>LAZY</td>
      <td>the propagation is totally lazy and this is default PHREAK behaviour/td>
    </tr>
</table>

## 相关概念

### OptaPlanner

#### NP NPC

#### 禁忌搜索

#### 模拟退火

#### 延迟接受

#### LHS RHS

### 什么是event和fact？

### forward chaining && backward chaining

### KRR (knowledge representation and reasoning)

### rete算法(drools5.x)

### First Order Logic

一阶逻辑是通过允许在给定论域的个体上的量化而扩展命题逻辑的演绎系统。
命题逻辑处理简单的陈述性命题，一阶逻辑补充覆盖了谓词和量化。

 [找了一篇稍微靠谱点的文章](https://blog.csdn.net/dragonszy/article/details/6939782)


----------------------------------------------------------------------------------------------------------------

<style type="text/css">
  table.hovertable {
    font-family: verdana, arial, sans-serif;
    font-size: 11px;
    color: #333333;
    border-width: 1px;
    border-color: #999999;
    border-collapse: collapse;
  }

  table.hovertable th {
    background-color: #c3dde0;
    border-width: 1px;
    padding: 8px;
    border-style: solid;
    border-color: #a9c6c9;
  }

  table.hovertable tr {
    background-color: #d4e3e5;
  }

  table.hovertable td {
    border-width: 1px;
    padding: 8px;
    border-style: solid;
    border-color: #a9c6c9;
  }
</style>
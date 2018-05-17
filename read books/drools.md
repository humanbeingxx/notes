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

RuleTableIdCard | RuleTableIdCard | RuleTableIdCard| RuleTableIdCard
---------|----------|---------|--------
----- | condition | condition | action
----- | p:person | p:person | p:person
----- | location | age >= $1 | issueIdCard($1)
----- | select person | select adults | issue id card
issue id card| beijing | 18 | p

问题是如果《民法》规定成年从18变成19，公安部需要同步修改规则。
这里可以使用inference将18岁的规则抽离。

- 立法部门维护年龄规则

RuleTableAge | RuleTableAge | RuleTableAge
---------|---------|--------
----- | condition | action
----- | p:person | -----
----- | age >= $1 | insert($1)
----- | select adults | adult relation
issue id card| 18 | newIsAdult(p)

- 公安部维护身份证发放规则

RuleTableIdCard | RuleTableIdCard | RuleTableIdCard | RuleTableIdCard
---------|----------|---------|--------
----- | condition | condition | action
----- | p:person | isAdult | -----
----- | location | person == $1 | issueIdCard($1)
----- | select person | select adults | issue id card
issue id card | beijing | p | p

### TMS(Truth Maintenanace System)

- [ ] 这个章节确实没看懂

### 使用决策表

*如果同时使用决策表和drl文件，kmodule中需要将base配置成不同的packages，否则用drls时会报NPE*

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

CONDITION | CONDITION | ACTION
---------|----------|---------
person:Person | person:Person | person:Person
age | gender | person.setColor("$param");
17 | male | red
17 | female | pink
19 | male | black
19 | female | purle

- 遇到的第一个问题：person.setColor("$param")不加分号，提示这里必须是一个boolean类型的表达式。加上分号，提示找不到@positional field。各种尝试后，发现需要将前两行的person:Person合并成一个单元格。。。**具体原理还不知道**
- 遇到的第二个问题：规则第一行不生效。xls的格式可能是固定的。age这个下面必须是注释。变成了

age | gender | person.setColor("$param");
---------|----------|---------
必须是注释 | 必须是注释 | 必须是注释
17 | male | red

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
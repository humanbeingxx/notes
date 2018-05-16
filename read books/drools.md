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
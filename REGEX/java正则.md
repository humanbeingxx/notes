# java正则

## 背景

转大盘需要处理labelValue，将value转化成octopus支持的格式，转成模糊搜索，转成in等。

例如 
`service =~ "tutor-.*"` 可以转成 `service = "tutor-*"`
`service =~ "tutor-.*|conan-.*"` 可以转成 `service in ("tutor-*", "conan-*")`


但是octopus的模糊搜索能力被做了限制，并不能自由支持正则。处理in时，也要考虑value中有没有其他特殊正则符号，否则按照|切分可能得到不正确的结果。

什么是特殊正则？对octopus来说，.*可以支持，.+也可以近似支持，同时也可以把简单的分支替换成in。但是怎么判断一个字符串是不是特殊正则？

## java正则解析

按照octopus删减后的Pattern类，重心在于pattern字符的处理，屏蔽了高级写法。

整体是按照字符顺序解析。核心分成 expr(), sequence(), atom()

expr将sequence返回的多个分支连接。在未删减的版本，(a|b)|c的表达式会解析成 branch:[group:branch:[a, b], c] 的形状。但删减版本直接不支持分支，不会有这样的返回。

atom会将尽可能多的普通字符组装成一个node，直到遇到特殊字符。注意，遇到转义符时不会停止，如果是不需要转义的字符，会忽略这个转义符。

java正则中可以转义或者说转义符可以生效的字符很多。例如 \\1 表示反向引用，\\A表示Begin，等等。

ps: \1是另一个含义，表示8进制。\11 == \u0009 == \t

参考: https://docs.oracle.com/javase/specs/jls/se7/html/jls-3.html#jls-3.10.6

sequence将atom返回的node组装成一个序列。但遇到右括号或者没有剩余字符时返回expr，如果有右括号，返回的将是一个GroupNode，表示捕获组。

### \p

表示匹配一类字符，https://www.compart.com/en/unicode/category

例如 \p{N} 不仅可以匹配阿拉伯数字，也可以匹配各种语种的数字，比如中文的'㆒'(\u3192)。

java中每一个category都有一位掩码。匹配时，先获取字符的类型(Character.getType(ch))，然后和这个掩码做&，等于1则满足条件。

## 执行过程

### 回溯

从一个case出发，pattern是`^(a*)*$ `，匹配`aaaaaaaaaaaaaaaaab`。

业务代码中可能不会写这样的正则，但是可能会写更复杂更特殊的，但核心也是有大量回溯的正则，务必注意。

回溯多少次？假设长度n的字符串需要S(n)次回溯，那么n+1长度需要多少？

```math
S(n) = S(n) + S(n-1) + ... + S(1)
\newline
S(n) = 2^n
```

并不是多层loop才能指数级回溯，分支过多也能达到效果，例如 `^(a|a|a|a|a|a|a|a|a|a|a|a|a|a|a|a|a|a|a|a|a|a|a|a|a|a|a|a|a|a|a|a|a|a|a|a|a|a|a|a|a|a|a|a|a|a|a|a|a|a|a|a|a|a|a|a|a|a|a|a|a)+$`

java8 vs java10+

用 IntHashSet[] localsPos 记住了每次匹配失败的位置。回溯时不必重头开始。

```java
    // Let's check if we have already tried and failed
    // at this starting position "i" in the past.
    // If yes, then just return false without trying
    // again, to stop the exponential backtracking.
    if (posIndex != -1 &&
        matcher.localsPos[posIndex].contains(i)) {
        return next.match(matcher, i, seq);
    }
```

```math
S(n) = \frac{n * (n+1)}{2}
```

 ### 零宽断言

1. 正向零宽先行断言：x(?=y)，表示：x 被 y 跟随时匹配 x
2. 正向零宽后发断言：(?<=y)x，表示：x 跟随 y 的情况下匹配 x
3. 负向零宽先行断言：x(?!y)，表示：x 没有被 y 紧随时匹配 x
4. 负向零宽后发断言：(?<!y)x，表示：x 不跟随 y 时匹配 x

case1

正则判断字符串中不包含ab

错误写法  ^(.(?!ab))*$
正确写法  ^((?!ab).)*$

case2

现有需要，要给一串数字添加","分隔，例如 1234567890 分隔成 1,234,567,890

正则表达式是从左向右匹配字符，这个case中不知道字符串的长度，因此在一次遍历过程中无法确定要在哪些位置添加逗号。所以这里要用到回溯从右向左完成替换。

在替换前，最右边是行终结符，每替换一次，会变成逗号。找到这个规律，可以写出如下正则 `(?<=\d)(?=\d{3}($|,))`。但是要完成替换需要多次执行正则。
 
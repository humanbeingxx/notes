# redis的统计结构

## HyperLogLog

用于不精确的统计UV，只保留统计数字，不保留具体的值。

redis命令：

- pfadd key element...
- pfcount key
- pfmerge destkey sourcekey...

### 原理

把一个element映射成64位数字，从最低位开始连续k个0出现的概率是 (1/2)^k，如果已知最长的连续0的长度是kmax，那么可以估算 n = 2^kmax。

通过引入不同的桶，解决单次实验偏差较大的问题。把64位数字hash到不同的桶中，每个桶单独估算后，取调和平均值。

调和平均：

```math
DV = \frac{m}{\sum^1_m\frac{1}{v_m}}
```

同时引入修正常数，具体值就不记了，规律是桶数越小，常数越小。

具体在redis中，64bit，前14位用于分配桶，后50位用于判断连续0的长度。

另一个常见问题是，为什么redis中的hll需要12K？50位最多连续50个0，用 2^6 即可保存长度，那么是 2^14 * 6 / 8 = 12KB

## BloomFilter

用于不精确的去重，更具体的，如果判断不存在，那就一定不存在，如果判断存在，可能是误判。

redis命令：

- bf.add key element
- bf.exists key element
- bf.madd key element...
- bf.mexists key element...

### 原理

使用 k 个hash函数，将element映射到 m 长度bit数组中，每个hash函数映射成一个数字，将该数字的位置为true。

k和m直接影响判断效果。计算公式如下：

```
预计元素数 n
预期错误率 f
数组长度 m
hash函数个数 k

f = 0.6185^(m/n) -> 计算数组长度 m
k = 0.7*(m/n) -> 计算出函数个数 k
```

### 拓展 CountingBloomFilter SpectralBloomFilter

普通的BloomFilter不支持删除，因此使用CountingBloomFilter，把原先的每bit变成了一个计数器。当添加元素且判断元素不存在时，需要将映射的每一位都+1，移除元素时，每一位都-1。

按照公式，每个计数器4bit就足够了。

SpectralBloomFilter在此基础上，支持了每个元素的出现频次。将BloomFilter的每一位都扩展成一个更大的计数器，那么元素的出现频次 >= 映射为1的最小计数。至于具体的优化存储实现，略过。
# git从0开始

## 零散知识点

### git维护了几个内置的特殊符号

#### HEAD

始终指向当前分支的最新提交

#### ORIG_HEAD

切换提交（比如reset）时，前一次所在的提交.
例如有提交 1 -> 2 -> 3，最新的是3

reset 2时，HEAD是2，ORIG_HEAD是3
再次reset 1时，ORIG_HEAD是2

#### FETCH_HEAD


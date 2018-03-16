# git push

## 模式

### matching

Git 1.x 的默认参数，也就是老的执行方式。其意是如果你执行 git push 但没有指定分支，它将 push 所有你本地的分支到远程仓库中对应匹配的分支。
simple（匹配单个分支）

### simple

Git 2.x 默认参数，意思是执行 git push 没有指定分支时，只有当前分支会被 push 到远程仓库。

### stream

git中存在upstream和downstream，简言之，当我们把仓库A中某分支x的代码push到仓库B分支y，此时仓库B的这个分支y就叫做A中x分支的upstream，而x则被称作y的downstream，这是一个相对关系，每一个本地分支都相对地可以有一个远程的upstream分支（注意这个upstream分支可以不同名，但通常我们都会使用同名分支作为upstream）。

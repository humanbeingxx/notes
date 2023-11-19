# jvm异常实现

## java异常

异常的出现总是由下面三种原因之一导致的:

- 虚拟机同步检测到程序发生了非正常的执行情况，这时异常将会紧接着在发生非正常执行 情况的字节码指令之后抛出。例如:
    1. 字节码指令所蕴含的操作违反了 Java 语言的语义，如访问一个超出数组边界范围的 元素。
    2. 类在加载或者链接时出现错误。
    3. 使用某些资源的时候产生资源限制，例如使用了太多的内存。

- athrow 字节码指令被执行。

- 由于以下原因，导致了异步异常的出现:
    1. 调用了 Thread 或者 ThreadGroup 的 stop 方法。
    2. Java 虚拟机实现的内部程序错误。

当某条线程调用了 stop 方法时，将会影响到其他的线程，或者在线程组中的所有线程。 这时候其他线程中出现的异常就是异步异常，因为这些异常可能出现在程序执行过程的任 何位置。虚拟机的内部异常也被认为是一种异步异常

## 引用自

> https://blog.csdn.net/iteye_10717/article/details/82572173#commentBox

## 源码解析

```java
public void catchException() {
    try {
        throw new Exception();
    } catch (Exception e) {
    }
}
```

对应的字节码

```java
public void catchException();
  Code:
   Stack=2, Locals=2, Args_size=1
   0:   new     #58; //class java/lang/Exception
   3:   dup
   4:   invokespecial   #60; //Method java/lang/Exception."<init>":()V
   7:   athrow
   8:   astore_1
   9:   return
  Exception table:
   from   to  target type
     0     8     8   Class java/lang/Exception

```

处理athrow的c++源码

```c++
CASE(_athrow): {
    oop except_oop = STACK_OBJECT(-1);
    CHECK_NULL(except_oop);
    // set pending_exception so we use common code
    THREAD->set_pending_exception(except_oop, NULL, 0);
    goto handle_exception;
}

handle_exception: {

  HandleMarkCleaner __hmc(THREAD);
  Handle except_oop(THREAD, THREAD->pending_exception());
  // Prevent any subsequent HandleMarkCleaner in the VM
  // from freeing the except_oop handle.
  HandleMark __hm(THREAD);

  THREAD->clear_pending_exception();
  assert(except_oop(), "No exception to process");
  intptr_t continuation_bci;
  // expression stack is emptied
  topOfStack = istate->stack_base() - Interpreter::stackElementWords;

  // 查找本地的异常表
  CALL_VM(continuation_bci = (intptr_t)InterpreterRuntime::exception_handler_for_exception(THREAD, except_oop()),
          handle_exception);

  except_oop = (oop) THREAD->vm_result();
  THREAD->set_vm_result(NULL);
  
  // 如果异常表找到处理方法，把异常重新入栈，并修改PC到对应的handler
  if (continuation_bci >= 0) {
    // Place exception on top of stack
    SET_STACK_OBJECT(except_oop(), 0);
    MORE_STACK(1);
    pc = METHOD->code_base() + continuation_bci;
    // for AbortVMOnException flag
    NOT_PRODUCT(Exceptions::debug_check_abort(except_oop));
    goto run;
  }

  // for AbortVMOnException flag
  NOT_PRODUCT(Exceptions::debug_check_abort(except_oop));
  // No handler in this activation, unwind and try again
  THREAD->set_pending_exception(except_oop(), NULL, 0);

  // 会根据pending_exception这个标志来决定是否有异常，要不要退出。
  goto handle_return;
}
```

## handle_return的后续动作

1. 当前方法栈帧出栈，回到调用函数。
2. 对当前函数（调用函数）做一次handler搜索，如果找不到，继续出栈，往上找。
3. 都找不到时，线程终止。
# jvm异常实现

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
3. 都找不到时，线程终止。
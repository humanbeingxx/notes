# 方法调用

invokevirtual 指令用于调用对象的实例方法，根据对象的实际类型进行分派(虚方法分 派)，这也是 Java 语言中最常见的方法分派方式。
invokeinterface 指令用于调用接口方法，它会在运行时搜索一个实现了这个接口方法的 对象，找出适合的方法进行调用。
invokespecial 指令用于调用一些需要特殊处理的实例方法，包括实例初始化方法(§ 2.9)、私有方法和父类方法。
invokestatic 指令用于调用类方法(static 方法)。

## 方法调用的解析

invokespecial和invokestatic在编译时就能确定调用目标，类加载时可以直接将符号引用转换成直接引用。
此类方法调用被称为解析。
符合条件的有static方法、构造器、私有方法、父类方法。此外final方法也是非虚方法，但是被invokevirtual指令调用。

## 方法调用的分派

### 静态分派

静态分派最经典的场景是重载方法。在编译期间完成。

`Human humna = new Man()`

代码中表达式左边的类型称为静态类型，右边被称为实际类型。
静态分配时看的是静态类型。会根据方法列表找到“更为合适”的版本。

***方法重载适配顺序***

```java
public class OverwriteSequenceTest {
    public static void say(Object arg) {
        System.out.println("object");
    }

    public static void say(int arg) {
        System.out.println("int");
    }

    public static void say(char arg) {
        System.out.println("char");
    }

    public static void say(long arg) {
        System.out.println("long");
    }

    public static void say(char... arg) {
        System.out.println("char...");
    }

    public static void say(Character arg) {
        System.out.println("Character");
    }

    public static void say(Serializable arg) {
        System.out.println("Serializable");
    }

    public static void main(String[] args) {
        say('a');

    }
}
```

顺序如下：

1. 精确匹配类型
2. 自动类型转换，char->int->long->float->double，但不会涉及到byte和short，因为转换不安全。
3. 自动装箱。
4. 按父类/接口层级往上找。
5. 变长参数。

### 动态分派

#### vtable 结构

在子类的vtable中，如果子类没有override，则父子类相同方法的地址入口一致。否则用子类入口覆盖父类入口。

```java
class A {  
  public void A(){  
  }  
  public void A(String a){  
  }  
}  

Class B extends A {
  public  void A(){  
  }  
  public void B(){
  }  
}  
```

B的vtable是 {B.A}{A.A(String)}{B.B}

#### itable结构

{itableOffsetEntry0}{itableOffsetEntry1}...{itableMethodEntry0}{itableMethodEntry1}...

itableOffsetEntry 保存的是interface,和对应的itableMethodEntry的偏移。
当有调用时，遍历itableOffsetEntry，找到匹配的entry后，再定位到itableMethodEntry。

### 静态分派和动态分派是可以同时进行的

```java

// Son继承Father，都有overwrite的choice方法

Father father = new Father();
Father son = new Son();
father.choice(new IPhone());
son.choice(new HuaWei());

```

上面的代码，实际会先静态分派，在动态分派。
静态分派时，会考察静态类型和参数类型，属于多（宗量）分派。实际生成的是Father.choice(IPhone)和Father.choice(HuaWei)。
动态分派时，只考虑方法接收对象的实际类型，属于单分派。

## 反射中的方法调用

method.invoke()方法，有下面几点原理：

- 每次获取method时，并不是直接new一个method，而是用ReflectionFactory.copyMethod复制一个。其内部调用了Method.copy方法。

```java
    Method copy() {
        // This routine enables sharing of MethodAccessor objects
        // among Method objects which refer to the same underlying
        // method in the VM. (All of this contortion is only necessary
        // because of the "accessibility" bit in AccessibleObject,
        // which implicitly requires that new java.lang.reflect
        // objects be fabricated for each reflective call on Class
        // objects.)
        if (this.root != null)
            throw new IllegalArgumentException("Can not copy a non-root Method");

        Method res = new Method(clazz, name, parameterTypes, returnType,
                                exceptionTypes, modifiers, slot, signature,
                                annotations, parameterAnnotations, annotationDefault);
        res.root = this;
        // Might as well eagerly propagate this if already present
        res.methodAccessor = methodAccessor;
        return res;
    }
```

其中最重要的就是共享了一个root和methodAccessor对象。当然第一次获取方法时，用的是getDeclaredMethods0本地方法获取的。

- invoke方法调用是通过MethodAccessor完成的。有三个实现
  - DelegatingMethodAccessorImpl，委托类，初次默认生成的就是这个。
  - NativeMethodAccessorImpl，当调用次数较少时，使用native方法invoke。
  - 动态生成的，当调用次数到一定次数（15次），用字节码生成一个高效的调用器。内部大致原理是直接调用方法对象的方法，例如：
  
```java
void func(String var){}

//生成的字节码大致如下

public Object invoke(Object obj, Object[] args)
    throws IllegalArgumentException, InvocationTargetException {
    // prepare the target and parameters
    if (obj == null) throw new NullPointerException();
    try {
        A target = (A) obj;
        if (args.length != 1) throw new IllegalArgumentException();
        String arg0 = (String) args[0];
    } catch (ClassCastException e) {
        throw new IllegalArgumentException(e.toString());
    } catch (NullPointerException e) {
        throw new IllegalArgumentException(e.toString());
    }
    // make the invocation
    try {
        target.func(arg0);
    } catch (Throwable t) {
        throw new InvocationTargetException(t);
    }
}
```

内部先是做了参数的还原，其中对参数个数、类型的判断都是结合原方法动态计算出来的。再是直接调用对象的方法 target.func(arg0)。

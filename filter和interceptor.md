# 区别

- filter是依赖于servlet容器的，没有servlet容器就无法回调doFilter方法，而interceptor与servlet无关；
- filter的过滤范围比interceptor大，filter除了过滤请求外通过通配符可以保护页面、图片、文件等，而interceptor只能过滤请求，只对action起作用，在action之前开始，在action完成后结束（如被拦截，不执行action）；
- filter的过滤一般在加载的时候在init方法声明，而interceptor可以通过在xml声明是guest请求还是user请求来辨别是否过滤；
- interceptor可以访问action上下文、值栈里的对象，而filter不能；
- 在action的生命周期中，拦截器可以被多次调用，而过滤器只能在容器初始化时被调用一次。
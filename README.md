# RetrofitCodeLearn
Retrofit 源码分析 设计模式分析 心得

# Retrofit 核心`create`

```kotlin
val service: GitHubService = retrofit.create(GitHubService::class.java)
```

```java
public <T> T create(final Class<T> service) {
    validateServiceInterface(service);
    return (T)
        Proxy.newProxyInstance(
            service.getClassLoader(),
            new Class<?>[] {service},
            new InvocationHandler() {
              private final Platform platform = Platform.get();
              private final Object[] emptyArgs = new Object[0];

              @Override
              public @Nullable Object invoke(Object proxy, Method method, @Nullable Object[] args)
                  throws Throwable {
                // If the method is a method from Object then defer to normal invocation.
                if (method.getDeclaringClass() == Object.class) {
                  return method.invoke(this, args);
                }
                args = args != null ? args : emptyArgs;
                return platform.isDefaultMethod(method)
                    ? platform.invokeDefaultMethod(method, service, proxy, args)
                    : loadServiceMethod(method).invoke(args);
              }
            });
}
```

通过动态代理  代理Service接口

## 大致的实现思路
代码运行期间通过动态代理 jdk帮我们实现 servicec接口 并有了具体的实现 而实现的具体内容正好是 `InvocationHandler.invoke(Object proxy, Method method, @Nullable Object[] args)`中的代码,也就是`loadServiceMethod(method).invoke(args);`
```

```


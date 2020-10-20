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

帮我们生成的代理类大概就长这个样子
```java
class ProxyClass implements GitHubService {
    
    InvocationHandler invocationHandler = new InvocationHandler() {
        @Override
        public Object invoke(Object proxy, Method method, @Nullable Object[] args) throws Throwable {
            return loadServiceMethod(method).invoke(args);
        }
    };
    
    @NotNull
    @Override
    public Call<Repo> listRepos(@NotNull String user) {
        return invocationHandler.invoke(this,this.getClass().getMethod("listRepos", String.class),user);
    }

}
```

一次正常的网络请求
```kotlin
val retrofit = Retrofit.Builder()
    .baseUrl("https://api.github.com/")
    .build()

val service: GitHubService = retrofit.create(GitHubService::class.java)

val call = service.listRepos("octocat")

call.enqueue(object : Callback<Repo?> {
    override fun onResponse(call: Call<Repo?>, response: Response<Repo?>) {
        Log.e("Main", "onResponse: ${response.body()?.name}" )
    }

    override fun onFailure(call: Call<Repo?>, t: Throwable) {

    }
})
```
经过`Retrofit.create`的代理后`Service`中定义的`Call`返回值实际上就是`loadServiceMethod(method).invoke(args)`

## loadServiceMethod
```java
ServiceMethod<?> loadServiceMethod(Method method) {
    ServiceMethod<?> result = serviceMethodCache.get(method);
    if (result != null) return result;

    synchronized (serviceMethodCache) {
      result = serviceMethodCache.get(method);
      if (result == null) {
        result = ServiceMethod.parseAnnotations(this, method);
        serviceMethodCache.put(method, result);
      }
    }
    return result;
  }
```
在这里进行了接口方法的缓存处理 优化性能 

## ServiceMethod.parseAnnotations

```

```


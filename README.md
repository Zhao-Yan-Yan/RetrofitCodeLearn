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
static <T> ServiceMethod<T> parseAnnotations(Retrofit retrofit, Method method) {
    RequestFactory requestFactory = RequestFactory.parseAnnotations(retrofit, method);

    Type returnType = method.getGenericReturnType();
    if (Utils.hasUnresolvableType(returnType)) {
      throw methodError(
          method,
          "Method return type must not include a type variable or wildcard: %s",
          returnType);
    }
    if (returnType == void.class) {
      throw methodError(method, "Service methods cannot return void.");
    }

    return HttpServiceMethod.parseAnnotations(retrofit, method, requestFactory);
}
```
`RequestFactory.parseAnnotations(retrofit, method);` 对声明的注解以及参数进行解析拼装

## HttpServiceMethod.parseAnnotations
```java
static <ResponseT, ReturnT> HttpServiceMethod<ResponseT, ReturnT> parseAnnotations(
      Retrofit retrofit, Method method, RequestFactory requestFactory) {
    //...省略
    if (!isKotlinSuspendFunction) {
      return new CallAdapted<>(requestFactory, callFactory, responseConverter, callAdapter);
    } else if (continuationWantsResponse) {
      //noinspection unchecked Kotlin compiler guarantees ReturnT to be Object.
      return (HttpServiceMethod<ResponseT, ReturnT>)
          new SuspendForResponse<>(
              requestFactory,
              callFactory,
              responseConverter,
              (CallAdapter<ResponseT, Call<ResponseT>>) callAdapter);
    } else {
      //noinspection unchecked Kotlin compiler guarantees ReturnT to be Object.
      return (HttpServiceMethod<ResponseT, ReturnT>)
          new SuspendForBody<>(
              requestFactory,
              callFactory,
              responseConverter,
              (CallAdapter<ResponseT, Call<ResponseT>>) callAdapter,
              continuationBodyNullable);
    }
}
```
默认情况下会return `CallAdapted`
```java
static final class CallAdapted<ResponseT, ReturnT> extends HttpServiceMethod<ResponseT, ReturnT> {
    private final CallAdapter<ResponseT, ReturnT> callAdapter;

    CallAdapted(
        RequestFactory requestFactory,
        okhttp3.Call.Factory callFactory,
        Converter<ResponseBody, ResponseT> responseConverter,
        CallAdapter<ResponseT, ReturnT> callAdapter) {
      super(requestFactory, callFactory, responseConverter);
      this.callAdapter = callAdapter;
    }

    @Override
    protected ReturnT adapt(Call<ResponseT> call, Object[] args) {
      return callAdapter.adapt(call);
    }
}
```
CallAdapted父类正好又是`HttpServiceMethod`
所以loadServiceMethod(method).invoke(args) 一路调用下来实际上是调用的`HttpServiceMethod`的`invoke`方法 如下

```java
@Override
  final @Nullable ReturnT invoke(Object[] args) {
    Call<ResponseT> call = new OkHttpCall<>(requestFactory, args, callFactory, responseConverter);
    return adapt(call, args);
}

protected abstract @Nullable ReturnT adapt(Call<ResponseT> call, Object[] args);
```

正好`adapt`是抽象方法 在 `CallAdapted` 中有实现 `callAdapter.adapt(call);`

`CallAdapted`中的`callAdapted`是从`HttpServiceMethod.parseAnnotations`这里传进来的

一路跟进
```java
 CallAdapter<ResponseT, ReturnT> callAdapter =
        createCallAdapter(retrofit, method, adapterType, annotations);
```
最终找到

Retrofit.nexCallAdapter
```java
public CallAdapter<?, ?> nextCallAdapter(
      @Nullable CallAdapter.Factory skipPast, Type returnType, Annotation[] annotations) {
    Objects.requireNonNull(returnType, "returnType == null");
    Objects.requireNonNull(annotations, "annotations == null");

    int start = callAdapterFactories.indexOf(skipPast) + 1;
    for (int i = start, count = callAdapterFactories.size(); i < count; i++) {
      CallAdapter<?, ?> adapter = callAdapterFactories.get(i).get(returnType, annotations, this);
      if (adapter != null) {
        return adapter;
      }
    }
    //...省略
}
```
返回的adapter 又是从callAdapterFactories.get().get()中取出来的

继续查看callAdapterFactories的来源

发现这个是在Retrofit的构造方法中初始化的,也就是Builder.build()中
```java
Executor callbackExecutor = this.callbackExecutor;
if (callbackExecutor == null) {
    callbackExecutor = platform.defaultCallbackExecutor();
}
List<CallAdapter.Factory> callAdapterFactories = new ArrayList<>(this.callAdapterFactories);
callAdapterFactories.addAll(platform.defaultCallAdapterFactories(callbackExecutor));
```
默认有一个callAdapterFactories 在Platform中

> Platform主要是对Java和Android的适配区分







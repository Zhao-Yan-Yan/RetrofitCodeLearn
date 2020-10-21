# RetrofitCodeLearn
Retrofit 源码分析 设计模式分析 心得

# 问题
1. 为什么我们调用接口方法没有具体实现就能请求网络 ?
2. Gson FastGson 等等 是如何转换的 Retrofit是怎样设计的 ?
3. Rxjava 这些 CallAdapter 又是如何设计的 ?
4. 为什么Retrofit的Call 在主线程中执行 ?
5. 我们在接口中声明的Call到底是谁 到底return了什么给我们调用 ?
6. 通过动态代理学到了什么 ?

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

```java
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
这里的OKHttpCall是网络请求的真正实现 最终还是`OkHttp`

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
这里的 callbackExecutor 在Androide的Okatfrorm 中有实现 
```java
static final class MainThreadExecutor implements Executor {
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void execute(Runnable r) {
        handler.post(r);
    }
}
```
是一个线程池  execute post到了主线程执行

默认有一个callAdapterFactories 在Platform中

> Platform主要是对Java和Android的适配区分

最终找到`DefaultCallAdapterFactory` 也就是 `platform.defaultCallAdapterFactories`中的

注意之前 `callAdapterFactories.get().get()` 两次get 第一次是集合`callAdapterFactories`的get 第二次是 `CallAdapter.Factory`的get
```java
public @Nullable CallAdapter<?, ?> get(
      Type returnType, Annotation[] annotations, Retrofit retrofit) {
 
    return new CallAdapter<Object, Call<?>>() {
      @Override
      public Type responseType() {
        return responseType;
      }

      @Override
      public Call<Object> adapt(Call<Object> call) {
        return executor == null ? call : new ExecutorCallbackCall<>(executor, call);
      }
    };
}
```
```

```
最终 return 一个 CallAdapter

回归到`callAdapter.adapt`  return `ExecutorCallbackCall`

这个Call 就是我们在Service中定义的最终使用的Call

也就是动态代理帮我们 return 的Call

```java
class ProxyClass implements GitHubService {
    @NotNull
    @Override
    public Call<Repo> listRepos(@NotNull String user) {
        return new ExecutorCallbackCall<>(executor, call);
    }
}
```

得到最终的调用流程

`Retrofit.loadServiceMethod(method)` --> `ServiceMethod.parseAnnotations(this, method)` --> `HttpServiceMethod.parseAnnotations(retrofit, method, requestFactory)` --> `return new CallAdapted<>(requestFactory, callFactory, responseConverter, callAdapter);`

`Retrofit.create` --> `Retrofit.loadServiceMethod(method).invoke(args)` --> `CallAdapted.invoke(args)` --> `adapt(call, args)` -->  `callAdapter.adapt(call)` --> `return new ExecutorCallbackCall<>(executor, call);`

## ExecutorCallbackCall分析
```java
static final class ExecutorCallbackCall<T> implements Call<T> {
    final Executor callbackExecutor;
    final Call<T> delegate;

    ExecutorCallbackCall(Executor callbackExecutor, Call<T> delegate) {
      this.callbackExecutor = callbackExecutor;
      this.delegate = delegate;
    }

    @Override
    public void enqueue(final Callback<T> callback) {
      Objects.requireNonNull(callback, "callback == null");

      delegate.enqueue(
          new Callback<T>() {
            @Override
            public void onResponse(Call<T> call, final Response<T> response) {
              callbackExecutor.execute(
                  () -> {
                    if (delegate.isCanceled()) {
                      // Emulate OkHttp's behavior of throwing/delivering an IOException on
                      // cancellation.
                      callback.onFailure(ExecutorCallbackCall.this, new IOException("Canceled"));
                    } else {
                      callback.onResponse(ExecutorCallbackCall.this, response);
                    }
                  });
            }

            @Override
            public void onFailure(Call<T> call, final Throwable t) {
              callbackExecutor.execute(() -> callback.onFailure(ExecutorCallbackCall.this, t));
            }
          });
    }
    //...省略
}
```

`callbackExecutor` 是一个运行在主线程的线程池

`delegate` 就是在 `invoke`传入`adapt`时创建的 `OkHttpCall` 进行实际的网络请求

所以得出结论Retrofit的Call 的回调是运行主线程的,并且再声明Service接口时 return 的 Call 不是 OKhttp的Call 实际是 ExecutorCallbackCall

# 问题

## Retrofit是如何适配RxJava的

通过 CallAdapter 通过判断方法的返回值类型 返回对应的 adapter

## Gson这些是如何转换的

OkHttpCall.enqueue 中进行实际的网络请求操作 并对结果进行处理

通过 responseConverter.convert(catchingBody); 进行转换

```
```

# 心得

`Converter.Factory`和`CallAdapter.Factory` 的设计 让 Retrofit 有了更高的拓展性  这正是高内聚低耦合的实现案例

一切设计应面向接口编程 让实际的核心操作 通过接口抽取 

动态代理的设计让方法的调用有了集中统一的管理

实际的应用

1. mvp中 presenter层 调用 view 层 可能会遇到 activity关闭 view已经null释放 presenter还在调用 出现null异常
这里我们就可以通过动态代理在调用view层之前判断view是否为空 统一管理 无需每次调用都判断

如何实现
```java
public abstract class BasePresenter<M extends IBaseModel, V extends IBaseView> {

    private V mProxyView;
    private M module;
    private WeakReference<V> weakReference;
    protected String TAG;

    public BasePresenter() {
        TAG = getClass().getSimpleName();
    }

    @SuppressWarnings("unchecked")
    public void attachView(V view) {
        weakReference = new WeakReference<>(view);
        mProxyView = (V) Proxy.newProxyInstance(view.getClass().getClassLoader(),view.getClass().getInterfaces(),new MvpViewHandler(weakReference.get()));
        if (this.module == null) {
            this.module = createModule();
        }
    }

    public void detachView() {
        this.module = null;
        if (isViewAttached()) {
            weakReference.clear();
            weakReference = null;
        }
    }

    protected boolean isViewAttached() {
        return weakReference != null && weakReference.get() != null;
    }

    protected V getView() {
        return mProxyView;
    }

    protected M getModule() {
        return module;
    }

    protected abstract M createModule();

    private class MvpViewHandler implements InvocationHandler {

        private IBaseView mvpView;

        MvpViewHandler(IBaseView mvpView) {
            this.mvpView = mvpView;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            //如果V层没被销毁, 执行V层的方法.
            //P层不需要关注V层的返回值
            if (isViewAttached()) {
                return method.invoke(mvpView, args);
            } 
            return null;
        }
    }
}
```
将view层的调用委托给MvpViewHandler 最终 invoke时统一加上 判断调用 method的invoke


# 答
CallAdapter 的适配流程

动态代理最终 `invoke` 会调用到 `HttpServiceMethod.invoke` ,  在 `HttpServiceMethod.invoke` 中会创建 `OkHttpCall`（这里是实际的Okhttp的请求）然后将 `OkHttpCall` 传递给 `HttpServiceMethod.adapt` 方法, 在 `CallAdapter` 中实现 调用的是 `callAdapter.adapt` . 这里的 `callAdapter` 实际上就是 `Retrofit` 初始化 `addCallAdapterFactory` 的一个 `CallAdapter`集合 , 在 `nextCallAdapter` 中遍历这个集合后调用 `get` 方法判断返回值是否为空 确定最终的 `CallAdapter` 然后调用该 `CallAdapter` 的 `adapt` 将 `OkHttpCall` 传入进行适配转换

```
Retrofit.create() 
    --> loadServiceMethod().invoke()
        --> ServiceMethod.parseAnnotations().invoke()
            --> HttpServiceMethod.parseAnnotations().invoke()
                --> new CallAdapted<>().invoke()
                    --> CallAdapted继承自HttpServiceMethod
                        --> HttpServiceMethod.invoke()
                            --> 创建OkHttpCall(实际的网络请求操作)
                                --> HttpServiceMetho.adapt(OkhHttpCall) //抽象方法 在子类(CallAdapted)有实现
                                    --> CallAdapted.adapt()
                                        --> callAdapter.adapt(call)

``` 
callAdapter 获取流程
```
HttpServiceMethod.parseAnnotations()
    --> HttpServiceMethod.createCallAdapter();
        --> retrofit.callAdapter()
            --> Retrofit.nextCallAdapter()
                --> callAdapterFactories.get(i).get(returnType, annotations, this)
                    --> new CallAdapted<>(callAdapter)
```
callAdapterFactories是一个集合 泛型`<CallAdapter.Factory>` `addCallAdapterFactory`都会添加到这个集合中

在 `CallAdapter.Factory` 的 `get` 方法中 根据返回值类型 确定是否是要转换适配的方法

如果是 实现具体的 `CallAdapter.adapt` 

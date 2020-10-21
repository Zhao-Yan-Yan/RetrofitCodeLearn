package com.zy.retrofitcodelearn;

import androidx.annotation.Nullable;

import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.List;

import io.reactivex.rxjava3.core.Single;
import retrofit2.Call;

/**
 * @ProjectName: RetrofitCodeLearn
 * @Author: 赵岩
 * @Email: 17635289240@163.com
 * @Description: TODO
 * @CreateDate: 2020/10/20 16:55
 */
class ProxyClass implements GitHubService {

//    InvocationHandler invocationHandler = new InvocationHandler() {
//        @Override
//        public Object invoke(Object proxy, Method method, @Nullable Object[] args) throws Throwable {
//            return loadServiceMethod(method).invoke(args);;
//        }
//    };
    // invocationHandler.invoke(this,this.getClass().getMethod("listRepos", String.class),user);


    @NotNull
    @Override
    public Single<List<Repo>> listRepos2(@NotNull String user) {
        return null;
    }

    @NotNull
    @Override
    public Call<List<Repo>> listRepos(@NotNull String user) {
        return null;
    }
}

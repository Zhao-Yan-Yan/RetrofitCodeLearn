package com.zy.retrofitcodelearn;

import java.lang.reflect.Type;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import retrofit2.Call;
import retrofit2.CallAdapter;
import retrofit2.Response;

/**
 * @ProjectName: RetrofitCodeLearn
 * @Author: 赵岩
 * @Email: 17635289240@163.com
 * @Description: TODO
 * @CreateDate: 2020/10/21 18:20
 */
class CustomRxJava3CallAdapter<R> implements CallAdapter<R, Object> {
    private final Type responseType;

    public CustomRxJava3CallAdapter(Type responseType) {
        this.responseType = responseType;
    }

    @Override
    public Type responseType() {
        return responseType;
    }

    @Override
    public Object adapt(Call<R> call) {
        Observable<Response<R>> responseObservable = new CallEnqueueObservable<>(call);
        return new BodyObservable<>(responseObservable)
                .singleOrError()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }
}

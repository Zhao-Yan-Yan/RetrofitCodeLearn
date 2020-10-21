package com.zy.retrofitcodelearn;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.exceptions.CompositeException;
import io.reactivex.rxjava3.exceptions.Exceptions;
import io.reactivex.rxjava3.plugins.RxJavaPlugins;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * @ProjectName: RetrofitCodeLearn
 * @Author: 赵岩
 * @Email: 17635289240@163.com
 * @Description: TODO
 * @CreateDate: 2020/10/21 18:25
 */

final class CallEnqueueObservable<T> extends Observable<Response<T>> {
    private final Call<T> originalCall;

    CallEnqueueObservable(Call<T> originalCall) {
        this.originalCall = originalCall;
    }

    @Override
    protected void subscribeActual(Observer<? super Response<T>> observer) {
        // Since Call is a one-shot type, clone it for each new observer.
        Call<T> call = originalCall.clone();
        CallEnqueueObservable.CallCallback<T> callback = new CallEnqueueObservable.CallCallback<>(call, observer);
        observer.onSubscribe(callback);
        if (!callback.isDisposed()) {
            call.enqueue(callback);
        }
    }

    private static final class CallCallback<T> implements Disposable, Callback<T> {
        private final Call<?> call;
        private final Observer<? super Response<T>> observer;
        private volatile boolean disposed;
        boolean terminated = false;

        CallCallback(Call<?> call, Observer<? super Response<T>> observer) {
            this.call = call;
            this.observer = observer;
        }

        @Override
        public void onResponse(Call<T> call, Response<T> response) {
            if (disposed) return;

            try {
                observer.onNext(response);

                if (!disposed) {
                    terminated = true;
                    observer.onComplete();
                }
            } catch (Throwable t) {
                Exceptions.throwIfFatal(t);
                if (terminated) {
                    RxJavaPlugins.onError(t);
                } else if (!disposed) {
                    try {
                        observer.onError(t);
                    } catch (Throwable inner) {
                        Exceptions.throwIfFatal(inner);
                        RxJavaPlugins.onError(new CompositeException(t, inner));
                    }
                }
            }
        }

        @Override
        public void onFailure(Call<T> call, Throwable t) {
            if (call.isCanceled()) {
                return;
            }

            try {
                observer.onError(t);
            } catch (Throwable inner) {
                Exceptions.throwIfFatal(inner);
                RxJavaPlugins.onError(new CompositeException(t, inner));
            }
        }

        @Override
        public void dispose() {
            disposed = true;
            call.cancel();
        }

        @Override
        public boolean isDisposed() {
            return disposed;
        }
    }
}

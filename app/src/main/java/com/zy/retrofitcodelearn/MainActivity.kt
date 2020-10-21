package com.zy.retrofitcodelearn

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Scheduler
import io.reactivex.rxjava3.core.SingleObserver
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val retrofit = Retrofit.Builder()
            .client(OkHttpClient())
            .baseUrl("https://api.github.com/")
            .addConverterFactory(GsonConverterFactory.create())
//            .addCallAdapterFactory(RxJava3CallAdapterFactory.createWithScheduler(Schedulers.io()))
            .addCallAdapterFactory(CustomRxJavaCallAdapter())
            .build()

        val service: GitHubService = retrofit.create(GitHubService::class.java)

        /*val call = service.listRepos("octocat")

        call.enqueue(object : Callback<Repo?> {
            override fun onResponse(call: Call<Repo?>, response: Response<Repo?>) {
                Log.e("Main", "onResponse: ${response.body()?.name}")
            }

            override fun onFailure(call: Call<Repo?>, t: Throwable) {

            }
        })*/
        /**
         *  .observeOn(AndroidSchedulers.mainThread())
         *  .subscribeOn(Schedulers.io())
         */
        service.listRepos2("octocat")
            .subscribe(object : SingleObserver<List<Repo>?> {
                override fun onSubscribe(d: Disposable?) {

                }

                override fun onSuccess(t: List<Repo>?) {
                    tv.text = t?.get(0)?.name
                    Log.e("TAG", "onSuccess: " + t?.get(0)?.name)
                    Log.e("TAG", "onSuccess: ${Thread.currentThread()}")
                }

                override fun onError(e: Throwable?) {
                    e?.printStackTrace()
                }
            })

        service.listRepos("Zhao-Yan-Yan").enqueue(object : Callback<List<Repo>?> {
            override fun onResponse(call: Call<List<Repo>?>, response: Response<List<Repo>?>) {
                val name = response.body()?.get(0)?.name
                tv.text = name
                Log.e("TAG", " listRepos onSuccess: $name")
                Log.e("TAG", " listRepos onSuccess: ${Thread.currentThread()}")
            }

            override fun onFailure(call: Call<List<Repo>?>, t: Throwable) {
            }
        })
    }
}
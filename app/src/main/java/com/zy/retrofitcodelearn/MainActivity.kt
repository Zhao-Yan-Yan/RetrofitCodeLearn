package com.zy.retrofitcodelearn

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
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
    }
}
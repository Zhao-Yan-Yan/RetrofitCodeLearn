package com.zy.retrofitcodelearn

import io.reactivex.rxjava3.core.Single
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * @ProjectName: RetrofitCodeLearn
 * @Author: 赵岩
 * @Email: 17635289240@163.com
 * @Description: TODO
 * @CreateDate: 2020/10/20 16:37
 */
interface GitHubService {

    @GET("users/{user}/repos")
    fun listRepos(@Path("user") user: String): Call<List<Repo>>

    @GET("users/{user}/repos")
    fun listRepos2(@Path("user") user: String): Single<List<Repo>>

}
package com.zy.retrofitcodelearn

import com.google.gson.annotations.SerializedName

/**
 * @ProjectName: RetrofitCodeLearn
 * @Author: 赵岩
 * @Email: 17635289240@163.com
 * @Description: TODO
 * @CreateDate: 2020/10/20 16:38
 */

data class Repo(
    @SerializedName("name") val name: String,
    @SerializedName("full_name") val fullName: String,
)
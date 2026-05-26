package com.hanto.kcandlekit.data

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface UpbitApiService {
    /**
     * 캔들 조회. interval = "days" | "weeks" | "months"
     * 결과는 최신 순(내림차순) — 호출 측에서 reversed() 처리.
     */
    @GET("candles/{interval}")
    suspend fun getCandles(
        @Path("interval") interval: String,
        @Query("market") market: String,
        @Query("count") count: Int,
    ): List<UpbitCandle>
}

object UpbitApi {
    val service: UpbitApiService by lazy {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .addHeader("Accept", "application/json")
                    .build()
                chain.proceed(req)
            }
            .build()

        Retrofit.Builder()
            .baseUrl("https://api.upbit.com/v1/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(UpbitApiService::class.java)
    }
}

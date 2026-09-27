package com.basket.data

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

/** Debug builds log request lines and status codes to Logcat (tag "okhttp.OkHttpClient"). */
fun OkHttpClient.Builder.addNetworkLogging(): OkHttpClient.Builder =
    addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })

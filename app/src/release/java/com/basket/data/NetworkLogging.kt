package com.basket.data

import okhttp3.OkHttpClient

/** Release builds do not log network traffic. */
fun OkHttpClient.Builder.addNetworkLogging(): OkHttpClient.Builder = this

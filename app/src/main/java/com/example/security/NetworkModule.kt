package com.example.security

import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.TlsVersion
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object NetworkModule {

    private const val VORTEX_USER_AGENT = "VortexMediaEngine/1.0 (Android; SecureHTTPS)"

    fun createSecureOkHttpClient(): OkHttpClient {
        val tlsSpec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2)
            .build()

        return OkHttpClient.Builder()
            .connectionSpecs(listOf(tlsSpec))
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val original = chain.request()
                // Enforce HTTPS on every hop including redirects
                if (!original.url.isHttps) {
                    throw SecurityException("Blocked non-HTTPS request to ${original.url.host}")
                }
                val request = original.newBuilder()
                    .header("User-Agent", VORTEX_USER_AGENT)
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    fun createRetrofit(client: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://localhost/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
    }
}

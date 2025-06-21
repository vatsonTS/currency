package com.example.currency.network

import com.example.currency.model.ConvertResponse
import com.example.currency.model.SymbolsResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface CurrencyApi {
    @GET("convert")
    suspend fun convert(
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("amount") amount: Double
    ): ConvertResponse

    @GET("symbols")
    suspend fun getSymbols(): SymbolsResponse
}

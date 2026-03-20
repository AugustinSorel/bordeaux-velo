package com.example.tp2_android

import retrofit2.http.GET

// --- MODELES DE DONNEES ---
data class V3Response(val records: List<V3Record>)
data class V3Record(val fields: V3Fields)
data class V3Fields(
    val nom: String,
    val nbvelos: Int,
    val nbplaces: Int,
    val etat: String,
    val geo_point_2d: List<Double>? = null
)
// --- INTERFACE API ---
interface VeloApi {
    @GET("api/records/1.0/search/?dataset=ci_vcub_p&rows=100")
    suspend fun getStations(): V3Response
}


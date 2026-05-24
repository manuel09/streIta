package it.dogior.hadEnough

import com.lagradost.cloudstream3.app

object TorBoxHelper {
    // Rimuovi l'uso di BuildConfig qui, lo passeremo dall'esterno
    
    suspend fun isCached(hash: String, apiKey: String): Boolean {
        val url = "https://api.torbox.app/v1/api/torrents/checkcached"
        val response = app.post(
            url,
            headers = mapOf("Authorization" to "Bearer $apiKey"),
            data = mapOf("hash" to hash)
        ).parsedSafe<TorBoxCheckCachedResponse>()
        return response?.success == true && response.data[hash] == true
    }

    suspend fun getStreamUrl(hash: String, apiKey: String): String? {
        val url = "https://api.torbox.app/v1/api/torrents/requestdl"
        val response = app.post(
            url,
            headers = mapOf("Authorization" to "Bearer $apiKey"),
            data = mapOf("hash" to hash, "file_id" to "0")
        ).parsedSafe<TorBoxDownloadResponse>()

        return response?.data?.url
    }
}

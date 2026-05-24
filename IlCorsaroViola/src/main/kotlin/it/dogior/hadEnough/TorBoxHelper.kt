package it.dogior.hadEnough

import com.lagradost.cloudstream3.app

object TorBoxHelper {

    // Funzione per verificare se l'hash è in cache
    suspend fun isCached(hash: String, apiKey: String): Boolean {
        val url = "https://api.torbox.app/v1/api/torrents/checkcached"
        
        val response = app.post(
            url,
            headers = mapOf("Authorization" to "Bearer $apiKey"),
            data = mapOf("hash" to hash)
        ).parsedSafe<TorBoxCheckCachedResponse>()
        
        // Controlla se la risposta contiene l'hash come 'true'
        return response?.success == true && response.data[hash] == true
    }
}

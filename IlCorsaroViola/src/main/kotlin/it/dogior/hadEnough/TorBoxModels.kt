package it.dogior.hadEnough

import com.fasterxml.jackson.annotation.JsonProperty

data class TorBoxCheckCachedResponse(
    val success: Boolean,
    val data: Map<String, Boolean> // Il formato API di TorBox restituisce una mappa hash -> boolean
)

data class TorBoxCreateTorrentResponse(
    val success: Boolean,
    val data: TorBoxTorrentData
)

data class TorBoxTorrentData(
    val torrent_id: String
)

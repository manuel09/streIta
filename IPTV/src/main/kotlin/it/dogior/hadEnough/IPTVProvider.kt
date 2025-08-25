package it.dogior.hadEnough

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import okhttp3.Interceptor

class IPTVProvider(override var mainUrl: String, override var name: String) : MainAPI() {
    override val hasMainPage = true
    override var lang = "un"
    override val hasQuickSearch = true
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(
        TvType.Live
    )

    private val items = mutableMapOf<String, Playlist?>()
    private val headers = mapOf("User-Agent" to "Player (Linux; Android 14)")

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        items[name] = IptvPlaylistParser().parseM3U(app.get(mainUrl, headers = headers).text)

        return newHomePageResponse(
            items[name]!!.items.groupBy { it.attributes["group-title"] }.map { group ->
                val title = group.key ?: ""
                val show = group.value.map { item ->
                    val streamurl = item.url.toString()
                    val channelname = item.title.toString()
                    val posterurl = item.attributes["tvg-logo"].toString()
                    val chGroup = item.attributes["group-title"].toString()
                    val key = item.attributes["key"].toString()
                    val keyid = item.attributes["keyid"].toString()

                    newLiveSearchResponse(
                        name = channelname,
                        url = LoadData(
                            streamurl,
                            channelname,
                            posterurl,
                            chGroup,
                            key,
                            keyid
                        ).toJson(),
                        type = TvType.Live
                    ) { this.posterUrl = posterurl }
                }

                HomePageList(title, show, isHorizontalImages = true)
            },
            hasNext = false
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        if (items[name] == null) {
            items[name] = IptvPlaylistParser().parseM3U(app.get(mainUrl, headers = headers).text)
        }

        return items[name]!!.items.filter {
            it.title.toString().lowercase().contains(query.lowercase())
        }.map { item ->
            val streamurl = item.url.toString()
            val channelname = item.title.toString()
            val posterurl = item.attributes["tvg-logo"].toString()
            val chGroup = item.attributes["group-title"].toString()
            val key = item.attributes["key"].toString()
            val keyid = item.attributes["keyid"].toString()

            newLiveSearchResponse(
                name = channelname,
                url = LoadData(
                    streamurl,
                    channelname,
                    posterurl,
                    chGroup,
                    key,
                    keyid
                ).toJson(),
                type = TvType.Live
            ) { this.posterUrl = posterurl }
        }
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse {
        val loadData = parseJson<LoadData>(url)
        if (items[name] == null) {
            items[name] = IptvPlaylistParser().parseM3U(app.get(mainUrl, headers = headers).text)
        }
        val recommendations = mutableListOf<LiveSearchResponse>()
        for (item in items[name]!!.items) {
            if (recommendations.size >= 24) break
            if (item.attributes["group-title"].toString() == loadData.group) {
                val rcStreamUrl = item.url.toString()
                val rcChannelName = item.title.toString()
                if (rcChannelName == loadData.title) continue

                val rcPosterUrl = item.attributes["tvg-logo"].toString()
                val rcChGroup = item.attributes["group-title"].toString()
                val key = item.attributes["key"].toString()
                val keyid = item.attributes["keyid"].toString()

                recommendations.add(
                    newLiveSearchResponse(
                        name = rcChannelName,
                        url = LoadData(
                            rcStreamUrl,
                            rcChannelName,
                            rcPosterUrl,
                            rcChGroup,
                            key,
                            keyid
                        ).toJson(),
                        type = TvType.Live
                    ) { this.posterUrl = rcPosterUrl }
                )
            }
        }

        return newLiveStreamLoadResponse(
            name = loadData.title,
            url = url, dataUrl = url,
        ) {
            posterUrl = loadData.poster
            this.recommendations = recommendations
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val loadData = parseJson<LoadData>(data)
        val item = items[name]!!.items.first { it.url == loadData.url }
        if (loadData.url.contains(".mpd")) {
            callback.invoke(
                newDrmExtractorLink(
                    name = this.name,
                    source = loadData.title,
                    url = loadData.url,
                    uuid = CLEARKEY_UUID
                ) {
                    this.kid = loadData.keyid.toString().trim()
                    this.key = loadData.key.toString().trim()
                }
                /* DrmExtractorLink(
                     name = this.name,
                     source = loadData.title,
                     url = loadData.url,
                     referer = "",
                     quality = Qualities.Unknown.value,
                     type = INFER_TYPE,
                     kid = loadData.keyid.toString().trim(),
                     key = loadData.key.toString().trim(),
                 )*/
            )
        } else {
            callback.invoke(

                newExtractorLink(
                    name = this.name,
                    source = loadData.title,
                    url = loadData.url,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.headers = item.headers
                    this.referer = item.headers["referrer"] ?: ""
                    this.quality = Qualities.Unknown.value
                }
            )
        }
        return true
    }

    @Suppress("ObjectLiteralToLambda")
    override fun getVideoInterceptor(extractorLink: ExtractorLink): Interceptor {
        return object : Interceptor {
            override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
                val request = chain.request()

                return chain.proceed(request)
            }
        }
    }
}

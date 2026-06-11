package it.dogior.hadEnough

import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SeasonData
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.LoadResponse.Companion.addScore
import com.lagradost.cloudstream3.addPoster
import com.lagradost.cloudstream3.addSeasonNames
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

class Tantifilm : MainAPI() {
    override var mainUrl = "https://tantifilm.online"
    override var name = "Tantifilm"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var lang = "it"
    override val hasMainPage = true

    override val mainPage = mainPageOf(
        "$mainUrl/trending?type=movie" to "Film di tendenza",
        "$mainUrl/trending?type=tv" to "Serie TV di tendenza",
        "$mainUrl/movies" to "Ultimi Film",
        "$mainUrl/movies?filter=top_rated" to "Film più votati",
        "$mainUrl/tvshows" to "Ultime Serie TV",
        "$mainUrl/tvshows?filter=top_rated" to "Serie TV più votate",
    )

    private fun parseCards(document: org.jsoup.nodes.Document): List<SearchResponse> {
        return document.select(".movie-card a").mapNotNull { card ->
            val url = card.attr("href")
            val poster = card.selectFirst("img")?.attr("src")
            val title = card.selectFirst("h3")?.text() ?: return@mapNotNull null
            val year = card.selectFirst("span.text-gray-300, span.text-sm")?.text()?.toIntOrNull()

            val isTv = url.startsWith("/tv/")
            if (isTv) {
                newTvSeriesSearchResponse(title, url, TvType.TvSeries) {
                    addPoster(poster)
                    this.year = year
                }
            } else {
                newMovieSearchResponse(title, url, TvType.Movie) {
                    addPoster(poster)
                    this.year = year
                }
            }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val url = if (page > 1) "${request.data}?page=$page" else request.data
        val document = app.get(url).document
        val items = parseCards(document)
        if (items.isEmpty()) return null

        val hasNext = document.selectFirst(".pagination .next") != null ||
                document.selectFirst("a[rel=next]") != null
        val section = HomePageList(request.name, items, false)
        return newHomePageResponse(section, hasNext)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search?query=$query"
        val document = app.get(url).document
        return parseCards(document)
    }

    private fun extractTmdbId(url: String): String? {
        return url.substringAfterLast("-").takeIf { it.all { c -> c.isDigit() } }
    }

    override suspend fun load(url: String): LoadResponse? {
        val fullUrl = if (url.startsWith("http")) url else "$mainUrl$url"
        val document = app.get(fullUrl).document
        val isTv = fullUrl.contains("/tv/")
        val tmdbId = extractTmdbId(url)

        val title = document.selectFirst("h1")?.ownText()?.trim()
            ?: document.selectFirst("h1")?.text()?.trim() ?: return null
        val poster = document.selectFirst("img[alt*=poster]")?.attr("src")
            ?: document.select("img").firstOrNull { it.attr("src").contains("/w500/") }?.attr("src")
        val backdrop = document.selectFirst("img[alt*=backdrop]")?.attr("src")
            ?: document.select("img").firstOrNull { it.attr("src").contains("/w1280/") }?.attr("src")

        val year = Regex("""\((\d{4})\)""").find(document.selectFirst("h1")?.text() ?: "")?.groupValues?.get(1)?.toIntOrNull()
        val rating = document.selectFirst(".text-yellow-400 + span, [class*=rating] span")?.text()?.toDoubleOrNull()
        val plot = document.selectFirst("p.text-lg")?.text()
            ?: document.selectFirst("[class*=overview] p")?.text()
        val tagline = document.selectFirst("p.italic")?.text()

        val iframeEl = document.selectFirst("#video-iframe")
        val iframeUrl = iframeEl?.attr("data-src")
            ?: iframeEl?.attr("src")

        val dataMap = mutableMapOf<String, String?>(
            "iframeUrl" to iframeUrl,
            "tmdbId" to tmdbId,
            "isTv" to isTv.toString(),
            "mainUrl" to mainUrl,
            "fullUrl" to fullUrl,
        )

        return if (!isTv) {
            val genres = document.select(".genre-pill").map { it.text().trim() }
            val durationText = document.selectFirst("[class*=duration] span")?.text()
                ?: document.select("*:containsOwn(min)").firstOrNull()?.text()
            val duration = Regex("""(\d+)h\s*(\d+)m""").find(durationText ?: "")?.let {
                val hours = it.groupValues[1].toInt()
                val minutes = it.groupValues[2].toInt()
                hours * 60 + minutes
            }

            newMovieLoadResponse(title, fullUrl, TvType.Movie, dataMap.toJson()) {
                addPoster(poster)
                this.backgroundPosterUrl = backdrop
                this.plot = plot
                this.year = year
                addScore(rating?.toString() ?: "")
                this.duration = duration
                this.tags = genres
            }
        } else {
            val seasons = mutableListOf<SeasonData>()
            val episodes = mutableListOf<Episode>()

            document.select("#season-select option").forEach { opt ->
                val seasonNum = opt.attr("value").toIntOrNull() ?: return@forEach
                val seasonName = opt.text().trim()
                seasons.add(SeasonData(seasonNum, seasonName))
            }

            if (seasons.isNotEmpty()) {
                val currentSeasonEps = document.select("#episode-select option").mapNotNull { opt ->
                    val epNum = opt.attr("value").toIntOrNull() ?: return@mapNotNull null
                    val epName = opt.text().trim()
                    val epData = mapOf(
                        "season" to seasons.first().season.toString(),
                        "episode" to epNum.toString(),
                        "tmdbId" to (tmdbId ?: ""),
                    )
                    newEpisode(epData.toJson()) {
                        this.name = epName
                        this.season = seasons.first().season
                        this.episode = epNum
                    }
                }
                episodes.addAll(currentSeasonEps)

                for (i in 1 until seasons.size) {
                    val seasonNum = seasons[i].season
                    try {
                        val seasonDoc = app.get("$fullUrl?season=$seasonNum").document
                        val seasonEps = seasonDoc.select("#episode-select option").mapNotNull { opt ->
                            val epNum = opt.attr("value").toIntOrNull() ?: return@mapNotNull null
                            val epName = opt.text().trim()
                            val epData = mapOf(
                                "season" to seasonNum.toString(),
                                "episode" to epNum.toString(),
                                "tmdbId" to (tmdbId ?: ""),
                            )
                            newEpisode(epData.toJson()) {
                                this.name = epName
                                this.season = seasonNum
                                this.episode = epNum
                            }
                        }
                        episodes.addAll(seasonEps)
                    } catch (_: Exception) { }
                }
            }

            newTvSeriesLoadResponse(title, fullUrl, TvType.TvSeries, episodes) {
                addPoster(poster)
                addSeasonNames(seasons)
                this.backgroundPosterUrl = backdrop
                this.plot = plot
                this.year = year
                addScore(rating?.toString() ?: "")
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        try {
            val json = org.json.JSONObject(data)
            val iframeUrl = json.optString("iframeUrl", null)
            val tmdbId = json.optString("tmdbId", null)
            val season = json.optString("season", null)
            val episode = json.optString("episode", null)

            if (season != null && episode != null && !tmdbId.isNullOrBlank()) {
                val tvUrl = "https://mappletv.uk/watch/tv/$tmdbId-$season-$episode"
                loadExtractor(tvUrl, "$mainUrl/", subtitleCallback, callback)
                return true
            }

            if (!iframeUrl.isNullOrBlank() && !iframeUrl.contains("{tv_id")) {
                loadExtractor(iframeUrl, "$mainUrl/", subtitleCallback, callback)
                return true
            }

            if (!tmdbId.isNullOrBlank()) {
                loadExtractor("https://mappletv.uk/watch/movie/$tmdbId", "$mainUrl/", subtitleCallback, callback)
                return true
            }
        } catch (_: Exception) { }

        return false
    }
}

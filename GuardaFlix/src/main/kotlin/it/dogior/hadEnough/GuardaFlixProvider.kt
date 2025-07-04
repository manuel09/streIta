package it.dogior.hadEnough

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jsoup.nodes.Document

import com.lagradost.cloudstream3.utils.loadExtractor

class GuardaFlixProvider : MainAPI() {
    override var mainUrl = "https://guardaflix.sbs"
    override var name = "GuardaFlix"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var lang = "it"
    override val hasMainPage = true

    override val mainPage = mainPageOf(
        "$mainUrl/film/" to "Ultimi Film",
        "$mainUrl/serie-tv/" to "Ultime Serie TV"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) {
            request.data
        } else {
            "${request.data}page/$page/"
        }
        val document = app.get(url).document

        val home = document.select("div.col-lg-3.col-md-4.col-xs-6").mapNotNull {
            val title = it.selectFirst("h3 > a")?.text() ?: return@mapNotNull null
            val href = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val posterUrl = it.selectFirst("img")?.attr("src")

            if (request.name == "Ultime Serie TV") {
                newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                    this.posterUrl = posterUrl
                }
            } else {
                newMovieSearchResponse(title, href, TvType.Movie) {
                    this.posterUrl = posterUrl
                }
            }
        }
        return HomePageResponse(listOf(HomePageList(request.name, home)))
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val document = app.get(url).document

        return document.select("div.col-lg-3.col-md-4.col-xs-6").mapNotNull {
            val title = it.selectFirst("h3 > a")?.text() ?: return@mapNotNull null
            val href = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val posterUrl = it.selectFirst("img")?.attr("src")

            if (href.contains("/serie-tv/")) {
                newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                    this.posterUrl = posterUrl
                }
            } else {
                newMovieSearchResponse(title, href, TvType.Movie) {
                    this.posterUrl = posterUrl
                }
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.selectFirst("h1.entry-title")?.text()?.trim() ?: ""
        val posterUrl = document.selectFirst("div.post-thumbnail img")?.attr("src")
        val plot = document.selectFirst("div.entry-content p")?.text()?.trim() ?: ""
        val year = document.selectFirst("span.year")?.text()?.toIntOrNull()

        return if (url.contains("/serie-tv/")) {
            val episodes = document.select("ul.episodes-list > li").mapNotNull {
                val a = it.selectFirst("a") ?: return@mapNotNull null
                val href = a.attr("href")
                val name = a.text()
                val season = it.parents().select("div.season-title").text().filter { it.isDigit() }.toIntOrNull()
                val episode = name.filter { it.isDigit() }.toIntOrNull()
                Episode(href, name, season, episode)
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = posterUrl
                this.year = year
                this.plot = plot
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = posterUrl
                this.year = year
                this.plot = plot
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val document = app.get(data).document
        document.select("iframe").forEach { iframe ->
            val src = iframe.attr("src")
            if (src.isNotEmpty()) {
                loadExtractor(src, data, subtitleCallback, callback)
            }
        }
        return true
    }
}
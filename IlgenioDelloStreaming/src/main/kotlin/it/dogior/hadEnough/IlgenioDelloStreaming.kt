package it.dogior.hadEnough

import android.util.Log
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addRating
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.addPoster
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import it.dogior.hadEnough.extractors.DroploadExtractor
import it.dogior.hadEnough.extractors.SupervideoExtractor
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.SocketTimeoutException

class IlGenioDelloStreaming : MainAPI() {
    override var mainUrl = "https://ilgeniodellostreaming.beer"
    override var name = "IlGenioDelloStreaming"
    override val supportedTypes = setOf(
        TvType.Movie, TvType.TvSeries,
        TvType.Cartoon, TvType.Anime, TvType.AnimeMovie, TvType.Documentary
    )
    override var lang = "it"
    override val hasMainPage = true

    override val mainPage = mainPageOf(
        "$mainUrl/film/" to "Film",
        "$mainUrl/serie-tv/" to "Serie TV",
        "$mainUrl/anime/" to "Anime",
        "$mainUrl/documentari/" to "Documentari",
        "$mainUrl/cartoni-animati/" to "Cartoni Animati"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val response = try {
            app.get(request.data).document
        } catch (e: SocketTimeoutException) {
            return null
        }
        val searchResponses = getItems(request.name, response)
        return newHomePageResponse(HomePageList(request.name, searchResponses), false)
    }

    private suspend fun getItems(section: String, page: Document): List<SearchResponse> {
        val searchResponses = when (section) {
            "Film", "Serie TV", "Anime", "Documentari", "Cartoni Animati" -> {
                val items = page.select(".item")
                items.map {
                    val title = it.select(".data > h3").text().trim()
                    val url = it.select("a").attr("href")
                    val poster = it.select("img").attr("src")
                    
                    newTvSeriesSearchResponse(title, url) {
                        this.posterUrl = poster
                    }
                }
            }
            else -> {
                Log.d("IlGenioDelloStreaming", "Unknown section: $section")
                emptyList()
            }
        }
        return searchResponses
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val response = app.get("$mainUrl/?s=$query")
        val page = response.document
        val items = page.select(".item")
        return items.map {
            val title = it.select(".data > h3").text().trim()
            val url = it.select("a").attr("href")
            val poster = it.select("img").attr("src")
            
            newTvSeriesSearchResponse(title, url) {
                this.posterUrl = poster
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val response = app.get(url).document
        val title = response.selectFirst(".data > h1")?.text()?.trim() ?: ""
        val poster = response.selectFirst(".poster > img")?.attr("src") ?: ""
        val rating = response.selectFirst(".rating")?.text()?.trim()?.removeSuffix("/10") ?: ""
        val year = response.selectFirst(".data > .extra > span:nth-child(1)")?.text()?.trim() ?: ""
        val duration = response.selectFirst(".data > .extra > span:nth-child(2)")?.text()?.trim() ?: ""
        val isMovie = url.contains("/film/")

        return if (isMovie) {
            val streamUrl = response.select(".player > iframe").map { it.attr("src") }
            val plot = response.select(".wp-content > p").text().trim()
            
            newMovieLoadResponse(title, url, TvType.Movie, streamUrl) {
                addPoster(poster)
                addRating(rating)
                this.duration = duration.toIntOrNull()
                this.year = year.toIntOrNull()
                this.plot = plot
            }
        } else {
            val episodes = getEpisodes(response)
            val plot = response.select(".wp-content > p").text().trim()
            
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                addPoster(poster)
                addRating(rating)
                this.year = year.toIntOrNull()
                this.plot = plot
            }
        }
    }

    private fun getEpisodes(page: Document): List<Episode> {
        val seasons = page.select(".seasons > .season")
        val episodes = mutableListOf<Episode>()
        
        seasons.forEach { season ->
            val seasonNum = season.selectFirst(".title")?.text()?.trim()?.substringAfter("Stagione ")?.toIntOrNull() ?: 1
            season.select(".episodes > li").forEach { ep ->
                val epNum = ep.selectFirst(".epnum")?.text()?.trim()?.toIntOrNull()
                val links = ep.select("a").map { it.attr("href") }
                
                episodes.add(Episode(links.toString()).apply {
                    this.season = seasonNum
                    this.episode = epNum
                })
            }
        }
        
        return episodes
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val links = parseJson<List<String>>(data)
        links.forEach {
            loadExtractor(it, subtitleCallback, callback)
        }
        return true
    }
}

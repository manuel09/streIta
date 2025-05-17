package it.dogior.hadEnough

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.fixUrl
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.newTvSeriesSearchResponse
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone


class Arte : MainAPI() {
    override var mainUrl = "https://www.arte.tv"
    override var name = "Arte"
    override val supportedTypes = setOf(TvType.Documentary)
    override var lang = "it"
    override val hasMainPage = true

    override val mainPage = mainPageOf(
        "$mainUrl/it" to "Home",
        "$mainUrl/it/search" to "Search"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val response = app.get(request.data)
        val document = response.document
        val sections = document.select("div[data-testid=\"zl-slider\"]")
        val searchResponses = sections.mapNotNull { section ->
            val sectionName = section.selectFirst("h2")?.text() ?: ""
            if (sectionName == "Cosa stai cercando?") return@mapNotNull null
            val videos = section.select("div[data-testid=\"ts-tsItem\"]")
            val searchResponses = videos.map { it.toSearchResponse() }
            val isHorizontal = sectionName != "Imperdibili"
            HomePageList(sectionName, searchResponses, isHorizontal)
        }
        return newHomePageResponse(searchResponses, false)
    }

    private fun Element.toSearchResponse(): SearchResponse {
        val name = this.select("h3").text()
        val name2 =
            this.select("a[data-testid=\"ts-tsItemLink\"]").text().removePrefix("Guarda ora ")
        val poster = this.select("img").attr("srcset")
            .substringAfterLast(",").substringBeforeLast(" ")
        val link = fixUrl(this.select("a[data-testid=\"ts-tsItemLink\"]").attr("href"))
        val title = name.ifEmpty { name2 }
        return newMovieSearchResponse(title, link) {
            posterUrl = poster
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "https://www.arte.tv/api/rproxy/emac/v4/it/web/pages/SEARCH/?page=1&query=$query"
        val response = app.get(url).body.string()
        val data = parseJson<SearchApiResponse>(response)
        val searchResponses = data.value.zones.first().content.data.map {
            val type = if (it.programId.startsWith("RC-")) TvType.TvSeries else TvType.Movie
            val title = it.title + " - " + it.subtitle
            val link = fixUrl(it.url)
            val image = it.image.url.replace("__SIZE__", "265x149")
            if (type == TvType.Movie) {
                newMovieSearchResponse(title, link) {
                    posterUrl = image
                }
            } else {
                newTvSeriesSearchResponse(title, link) {
                    posterUrl = image
                }
            }
        }

        return searchResponses
    }

    override suspend fun load(url: String): LoadResponse {
        val type = if (url.substringAfter("/videos/").startsWith("RC"))
            TvType.TvSeries else TvType.Movie
        val respoonse = app.get(url)
        val document = respoonse.document
        val title = document.select("meta[property=\"og:title\"]")
            .attr("content").substringBeforeLast("-")
        val plot = document.select("meta[property=\"og:description\"]")
            .attr("content")
        val image = document.select("meta[property=\"og:image\"]")
            .attr("content")

        return if (type == TvType.Movie) {
            val link = extractVideoUrl(document)
            newMovieLoadResponse(title, url, type, link) {
                posterUrl = image
                this.plot = plot
            }
        } else {
            val showId = url.substringAfter("/videos/").substringBefore("/")
            val episodes = getEpisodes(showId)
            newTvSeriesLoadResponse(title, url, type, episodes) {
                posterUrl = image
                this.plot = plot
            }
        }
    }

    private suspend fun getEpisodes(showId: String): List<Episode> {
        val episodes = mutableListOf<Episode>()

        val apiUrl =
            "https://www.arte.tv/api/rproxy/emac/v4/it/web/zones/adcc5a2e-85f2-4dfb-9fc6-72755ff56267/content?abv=A&authorizedCountry=IT&collectionId=$showId&page=1&pageId=collection&type=collection&zoneIndexInPage=1"
        val response = app.get(apiUrl).body.string()
        val parsedResponse = parseJson<ShowApiResponse>(response)
        val totalPages = parsedResponse.value.pagination?.totalPages ?: 1

        val currentPage = 1
        (currentPage..totalPages).toList().amap { page ->
            val apiUrl2 =
                "https://www.arte.tv/api/rproxy/emac/v4/it/web/zones/adcc5a2e-85f2-4dfb-9fc6-72755ff56267/content?abv=A&authorizedCountry=IT&collectionId=$showId&page=$page&pageId=collection&type=collection&zoneIndexInPage=1"
            val response2 = app.get(apiUrl2).body.string()
            val parsedResponse2 = parseJson<ShowApiResponse>(response2)
            var epNumber = 0
            episodes.addAll(parsedResponse2.value.data.mapNotNull {
                if (it.type != "trailer") {
                    val episodePage = app.get(fixUrl(it.url)).document
                    val link = extractVideoUrl(episodePage)
                    epNumber++
                    newEpisode(link ?: "") {
                        this.name = it.title
                        this.description = it.description + "\n" + it.availability.label
                        this.posterUrl = it.image.url.replace("__SIZE__", "265x149")
                        this.runTime = it.duration / 60
                        this.date = convertToUnixTimestamp(it.availability.start)
                        this.episode = epNumber
                        this.season = page
                    }
                } else {
                    null
                }
            })
        }

        return episodes
    }

    private fun convertToUnixTimestamp(isoTimestamp: String): Long? {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
            sdf.timeZone = TimeZone.getDefault()

            val date = sdf.parse(isoTimestamp)

            date?.let { it.time }
        } catch (e: Exception) {
            null
        }
    }

    private fun extractVideoUrl(page: Document): String? {
        val urlRegex = Regex("https://manifest-arte.akamaized.net/.+m3u8")
        val script = page.select("script").firstOrNull { it.data().contains("akamaized") }?.data()
            ?: return null
        val link = urlRegex.find(script)?.value
        return link
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        Log.d("ARTE", data)
        if (data.isEmpty()) return false
        callback(
            newExtractorLink(
                this.name,
                this.name,
                data,
                type = INFER_TYPE
            )
        )
        return true
    }

    data class ShowApiResponse(
        @JsonProperty("value") val value: Value,
    )

    data class Value(
        @JsonProperty("data") val data: List<EpisodeData>,
        @JsonProperty("pagination") val pagination: Pagination?,
    )

    data class EpisodeData(
        @JsonProperty("mainImage") val image: ShowImage,
        @JsonProperty("shortDescription") val description: String,
        @JsonProperty("title") val title: String,
        @JsonProperty("type") val type: String,
        @JsonProperty("duration") val duration: Int, //In minutes
        @JsonProperty("url") val url: String,
        @JsonProperty("availability") val availability: Availability,
    )

    data class Availability(
        @JsonProperty("start") val start: String,
        @JsonProperty("label") val label: String,
    )

    data class ShowImage(
        @JsonProperty("url") val url: String,
    )

    data class Pagination(
        @JsonProperty("page") val currentPage: Int,
        @JsonProperty("pages") val totalPages: Int,
    )

    data class SearchApiResponse(
        @JsonProperty("value") val value: Value2,
    )

    data class Value2(
        @JsonProperty("zones") val zones: List<Zones>,
    )

    data class Zones(
        @JsonProperty("content") val content: SearchContent,
    )

    data class SearchContent(
        @JsonProperty("data") val data: List<SearchData>,
    )

    data class SearchData(
        @JsonProperty("mainImage") val image: ShowImage,
        @JsonProperty("shortDescription") val description: String,
        @JsonProperty("title") val title: String,
        @JsonProperty("subtitle") val subtitle: String,
        @JsonProperty("programId") val programId: String,
        @JsonProperty("url") val url: String,
    )
}
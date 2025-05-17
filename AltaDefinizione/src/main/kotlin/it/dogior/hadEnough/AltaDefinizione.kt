package it.dogior.hadEnough

import com.lagradost.api.Log
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.extractors.Supervideo
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import it.dogior.hadEnough.extractors.DroploadExtractor
import it.dogior.hadEnough.extractors.MyMixdropExtractor
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addRating
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class AltaDefinizione : MainAPI() {
    override var mainUrl = "https://altadefinizionegratis.store"
    override var name = "AltaDefinizione"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Documentary)
    override var lang = "it"
    override val hasMainPage = true

    override val mainPage = mainPageOf(
        "$mainUrl/film/" to "Ultimi Aggiunti",
        "$mainUrl/cinema/" to "Ora al Cinema",
        "$mainUrl/netflix-streaming/" to "Netflix",
        "$mainUrl/animazione/" to "Animazione",
        "$mainUrl/avventura/" to "Avventura",
        "$mainUrl/azione/" to "Azione",
        "$mainUrl/biografico/" to "Biografico",
        "$mainUrl/commedia/" to "Commedia",
        "$mainUrl/crime/" to "Crimine",
        "$mainUrl/documentario/" to "Documentario",
        "$mainUrl/drammatico/" to "Drammatico",
        "$mainUrl/erotico/" to "Erotico",
        "$mainUrl/famiglia/" to "Familiare",
        "$mainUrl/fantascienza/" to "Fantascienza",
        "$mainUrl/fantasy/" to "Fantasy",
        "$mainUrl/giallo/" to "Giallo",
        "$mainUrl/guerra/" to "Guerra",
        "$mainUrl/horror/" to "Horror",
        "$mainUrl/musical/" to "Musical",
        "$mainUrl/poliziesco/" to "Poliziesco",
        "$mainUrl/romantico/" to "Romantico",
        "$mainUrl/sportivo/" to "Sportivo",
        "$mainUrl/storico-streaming/" to "Storico",
        "$mainUrl/thriller/" to "Thriller",
        "$mainUrl/western/" to "Western"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = "${request.data.trimEnd('/')}/page/$page/"
        val doc = app.get(url).document
        val items = doc.select("div.wrapperImage").mapNotNull {
            it.toSearchResponse()
        }
        val pagination = doc.select(".pages").select("a").last()?.text()?.toIntOrNull()
        val hasNext = page < (pagination ?: 0)

        return newHomePageResponse(HomePageList(request.name, items), hasNext = hasNext)
    }

    private fun Element.toSearchResponse(): MovieSearchResponse? {
        val aTag = this.selectFirst("a[href]") ?: return null
        val img = this.selectFirst("img")?.attr("src") ?: return null
        val title = this.selectFirst("h2.titleFilm a")?.text()?.trim() ?: return null
        val href = fixUrl(aTag.attr("href"))
        val poster = fixUrl(img)
        val quality = this.selectFirst("span.hd")?.text()
        return newMovieSearchResponse(title, href) {
            this.posterUrl = poster
            quality?.let {
                val qualityMap = mapOf(
                    "HD" to SearchQuality.HD,
                    "SD" to SearchQuality.SD,
                    "CAM" to SearchQuality.Cam,
                    "TS" to SearchQuality.Cam
                )
                this.quality = qualityMap.getOrDefault(
                    qualityMap.keys.firstOrNull { q ->
                    q in it
                }, null)
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get(
            "$mainUrl/", params = mapOf(
                "story" to query,
                "do" to "search",
                "subaction" to "search",
                "titleonly" to "3"
            )
        ).document

        return doc.select("div.wrapperImage").mapNotNull {
            it.toSearchResponse()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document

        val title = doc.selectFirst("h1")?.text()?.trim()?.removeSuffix(" streaming") ?: "Sconosciuto"
        val poster = doc.selectFirst("div.col-lg-3.thumbPhoto img")?.attr("src")?.let { fixUrl(it) }
        val plot = doc.selectFirst("p#sfull")?.ownText()?.trim()?.substringAfter("Trama: ")
        val genres = doc.select("#details > li:nth-child(1)").select("a")
        val year = doc.select("li")
            .firstOrNull { it.selectFirst("label")?.text()?.contains("Anno") == true }
            ?.ownText()?.trim()
        val rating = doc.select(".rateIMDB").text().replace("IMDb: ", "")
        val episodes = getEpisodes(doc)

        val tags = genres.map { it.text() }

        return if (episodes.size > 1) {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.tags = tags
                addRating(rating)
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, episodes.firstOrNull()?.data ?: "") {
                this.posterUrl = poster
                this.plot = plot
                this.tags = tags
                this.year = year?.toInt()
                addRating(rating)
            }
        }
    }

    private suspend fun getEpisodes(doc: Document): List<Episode> {
        val episodes = mutableListOf<Episode>()
        val supportedHosts = listOf("supervideo", "dropload", "mixdrop", "doodstream")

        val seasonDivs = doc.select("div.tab-pane[id^=season-]")
        if (seasonDivs.isNotEmpty()) {
            for (seasonDiv in seasonDivs) {
                val seasonNum =
                    seasonDiv.attr("id").removePrefix("season-").toIntOrNull() ?: continue
                for (li in seasonDiv.select("ul > li")) {
                    val aTag = li.selectFirst("a[data-link]") ?: continue
                    val epNum = aTag.attr("data-num").toIntOrNull()
                    val epTitle = aTag.attr("data-title").ifBlank { "Episodio $epNum" }
                    val mirrors = li.select("div.mirrors a.mr")

                    val links = mirrors.mapNotNull {
                        val url = it.attr("data-link")
                        val host = supportedHosts.find { h -> url.contains(h) }
                        if (host != null) VideoStream(host, url) else null
                    }

                    if (links.isNotEmpty()) {
                        episodes.add(
                            newEpisode(links.toJson()) {
                                this.name = epTitle
                                this.season = seasonNum
                                this.episode = epNum
                            }
                        )
                    }
                }
            }
        } else {
            var rows = doc.select("table#download-table tr[onclick]")
            val sources = mutableListOf<VideoStream>()

            if (rows.isEmpty()) {
                val script = doc.selectFirst("script[src*=\"guardahd.stream/ddl/\"]")?.attr("src")
                if (script != null) {
                    val scriptUrl = when {
                        script.startsWith("//") -> "https:$script"
                        script.startsWith("/") -> "https://guardahd.stream$script"
                        else -> script
                    }
                    val jsText = app.get(scriptUrl).text
                    val htmlParts = jsText.lines()
                        .filter { it.trim().startsWith("document.write(") }
                        .map {
                            it.trim()
                                .removePrefix("document.write(\"")
                                .removeSuffix("\");")
                                .replace("\\'", "'")
                        }
                    val fakeHtml = htmlParts.joinToString("")
                    val generatedDoc = Jsoup.parse(fakeHtml)
                    rows = generatedDoc.select("table#download-table tr[onclick]")
                }
            }

            for (row in rows) {
                val onclick = row.attr("onclick")
                val link =
                    Regex("""window\.open\(\s*['"]([^'"]+)['"]""").find(onclick)?.groupValues?.get(1)
                if (link == null || link.contains("mostraguarda.stream")) continue
                val host = supportedHosts.find { link.contains(it) } ?: continue
                sources.add(VideoStream(host, link))
            }

            if (sources.isNotEmpty()) {
                episodes.add(newEpisode(sources.toJson()).apply { name = "Film Completo" })
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
        Log.d("AltaDefinizione", data)
        val parsedData = parseJson<List<VideoStream>>(data)

        parsedData.map {
            if (it.host.contains("mixdrop")) {
                MyMixdropExtractor().getUrl(it.url, "", subtitleCallback, callback)
            } else if (it.host.contains("dropload")) {
                DroploadExtractor().getUrl(it.url, "", subtitleCallback, callback)
            } else if (it.host.contains("supervideo")) {
                Supervideo().getUrl(it.url, "", subtitleCallback, callback)
            }
//            loadExtractor(it.url, data, subtitleCallback, callback)
        }
        return false
    }

    data class VideoStream(
        val host: String,
        val url: String
    )
}
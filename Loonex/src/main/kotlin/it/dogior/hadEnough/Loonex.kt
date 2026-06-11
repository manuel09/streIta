package it.dogior.hadEnough

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element

class Loonex : MainAPI() {
    override var mainUrl = "https://loonex.eu"
    override var name = "Loonex"
    override val supportedTypes = setOf(TvType.Cartoon, TvType.Anime, TvType.Movie, TvType.TvSeries)
    override var lang = "it"
    override val hasMainPage = true

    private val cartoonBase get() = "$mainUrl/cartoni"
    private val guardaBase get() = "$mainUrl/guarda"

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page <= 1) {
            cartoonBase
        } else {
            "$cartoonBase/?cat=all&search=&collezione=&page=$page"
        }
        val doc = app.get(url).document
        val lists = mutableListOf<HomePageList>()
        val seenUrls = mutableSetOf<String>()

        if (page <= 1) {
            doc.select("div.seamless-band").forEach { band ->
                val titleEl = band.select("h3").firstOrNull() ?: return@forEach
                val title = titleEl.text().trim()
                if (title.isBlank()) return@forEach
                val cards = band.select("a[href*=\"?cartone=\"]").mapNotNull { el ->
                    parseCard(el)?.takeIf { seenUrls.add(it.url) }
                }
                if (cards.isNotEmpty()) {
                    lists.add(HomePageList(title, cards))
                }
            }
        }

        val sections = doc.select("h3.cat-title, h3.brand-font, h2")
        for (section in sections) {
            val sectionTitle = section.ownText().trim().ifEmpty {
                section.text().trim()
            }
            if (sectionTitle.isBlank()) continue

            val cards = mutableListOf<SearchResponse>()
            var container = section.nextElementSibling()
            while (container != null && container.tagName() != "h2" && container.tagName() != "h3") {
                val items = container.select("a[href*=\"?cartone=\"]")
                for (item in items) {
                    val searchResp = parseCard(item) ?: continue
                    if (seenUrls.add(searchResp.url)) {
                        cards.add(searchResp)
                    }
                }
                container = container.nextElementSibling()
            }

            if (cards.isNotEmpty()) {
                lists.add(HomePageList(sectionTitle, cards))
            }
        }

        if (lists.isEmpty()) {
            val allCards = doc.select("a[href*=\"?cartone=\"]").mapNotNull { parseCard(it) }.distinctBy { it.url }
            if (allCards.isNotEmpty()) {
                lists.add(HomePageList("Tutti i cartoni", allCards))
            }
        }

        val maxPage = doc.select("ul.pagination a.page-link[href*=\"page=\"]").mapNotNull { link ->
            Regex("""page=(\d+)""").find(link.attr("href"))?.groupValues?.get(1)?.toIntOrNull()
        }.maxOrNull() ?: 1

        return newHomePageResponse(lists, page < maxPage)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$cartoonBase/?search=$query&cat=all"
        val doc = app.get(url).document
        return doc.select("a[href*=\"?cartone=\"]").mapNotNull { parseCard(it) }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document

        val title = doc.select("h1").text().ifEmpty {
            doc.select("meta[property=\"og:title\"]").attr("content")
                .replace(" | Streaming ITA GRATIS", "")
                .ifEmpty { "Sconosciuto" }
        }
        val plot = doc.select("meta[property=\"og:description\"]").attr("content")
        val poster = doc.select("meta[property=\"og:image\"]").attr("content")
            .ifEmpty {
                doc.select("img.card-img-bg, img.detail-poster").firstOrNull()?.attr("abs:src") ?: ""
            }

        val episodeLinks = doc.select("a[href*=\"/guarda/?id=\"]")
        val ogDesc = doc.select("meta[property=\"og:description\"]").attr("content")
        val isMovie = episodeLinks.size <= 1 ||
            doc.text().contains("FILM COMPLETO") ||
            ogDesc.contains("FILM COMPLETO")

        if (isMovie) {
            val episodeId = episodeLinks.firstOrNull()?.attr("href")?.substringAfter("?id=") ?: ""
            return newMovieLoadResponse(title, url, TvType.Movie, episodeId) {
                this.posterUrl = poster
                this.plot = plot
            }
        }

        val episodes = mutableListOf<Episode>()
        val tabPanes = doc.select("div.tab-pane")

        if (tabPanes.isNotEmpty()) {
            for (tabPane in tabPanes) {
                val heading = tabPane.select("h5").text().ifEmpty { "Episodi" }
                val epRows = tabPane.select("div.episode-row")
                for (epRow in epRows) {
                    val ep = parseEpisodeRow(epRow, heading) ?: continue
                    episodes.add(ep)
                }
            }
        } else {
            for (link in episodeLinks) {
                val epRow = link.closest("div.episode-row")
                if (epRow != null) {
                    val ep = parseEpisodeRow(epRow, "Episodi") ?: continue
                    episodes.add(ep)
                } else {
                    val epName = link.parent()?.ownText()?.trim()
                        ?: link.previousElementSibling()?.text()?.trim()
                        ?: "Episodio"
                    val episodeId = link.attr("href").substringAfter("?id=")
                    episodes.add(newEpisode(episodeId) { this.name = epName })
                }
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
            this.plot = plot
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        if (data.isEmpty()) return false

        val m3u8Url = if (data.startsWith("http")) {
            data
        } else {
            val guardaUrl = "$guardaBase/?id=$data"
            val guardaDoc = app.get(
                guardaUrl,
                headers = mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            ).text
            decryptVideoUrl(guardaDoc) ?: return false
        }

        callback(
            newExtractorLink(
                this.name,
                this.name,
                m3u8Url,
                type = ExtractorLinkType.M3U8
            ) {
                this.referer = "$guardaBase/"
                this.quality = Qualities.Unknown.value
            }
        )
        return true
    }

    private fun parseCard(element: Element): SearchResponse? {
        val fullUrl = element.absUrl("href").ifEmpty {
            val href = element.attr("href")
            if (href.startsWith("http")) href else "$cartoonBase/$href"
        }
        val img = element.select("img.card-img-bg").firstOrNull() ?: element.select("img").firstOrNull()
        val posterUrl = img?.attr("abs:src")?.takeIf { it.isNotBlank() }
        val title = element.select("div.card-title-cine").text().ifEmpty {
            (img?.attr("alt") ?: "").trim()
        }
        if (title.isBlank()) return null

        val isMovie = element.text().contains("FILM COMPLETO")
        val type = if (isMovie) TvType.Movie else TvType.TvSeries

        return if (type == TvType.Movie) {
            newMovieSearchResponse(title, fullUrl, type, false) {
                this.posterUrl = posterUrl
            }
        } else {
            newTvSeriesSearchResponse(title, fullUrl, type, false) {
                this.posterUrl = posterUrl
            }
        }
    }

    private fun parseEpisodeRow(epRow: Element, seasonName: String): Episode? {
        val link = epRow.select("a[href*=\"/guarda/?id=\"]").firstOrNull() ?: return null
        val episodeId = link.attr("href").substringAfter("?id=")
        if (episodeId.isBlank()) return null
        val epTitle = epRow.select("span.episode-title").text().ifEmpty {
            epRow.ownText().trim().ifEmpty { "Episodio" }
        }

        val (season, episode) = parseSeasonEpisode(epTitle)

        return newEpisode(episodeId) {
            this.name = epTitle
            this.season = season
            this.episode = episode
        }
    }

    private fun parseSeasonEpisode(title: String): Pair<Int?, Int?> {
        val regex = Regex("""(\d+)x(\d+)""")
        val match = regex.find(title)
        if (match != null) {
            return Pair(match.groupValues[1].toIntOrNull(), match.groupValues[2].toIntOrNull())
        }
        return Pair(null, null)
    }

    private fun decryptVideoUrl(pageText: String): String? {
        val encodedMatch = Regex("""var\s+encodedStr\s*=\s*"([^"]+)""").find(pageText) ?: return null
        val keyMatch = Regex("""var\s+decryptionKey\s*=\s*"([^"]+)""").find(pageText) ?: return null

        val hexStr = encodedMatch.groupValues[1]
        val key = keyMatch.groupValues[1]

        if (hexStr.isEmpty() || key.isEmpty()) return null

        val decrypted = StringBuilder()
        for (i in hexStr.indices step 2) {
            val charCode = hexStr.substring(i, i + 2).toIntOrNull(16) ?: continue
            val keyChar = key[(i / 2) % key.length].code
            decrypted.append((charCode xor keyChar).toChar())
        }

        return try {
            java.net.URLDecoder.decode(decrypted.toString(), "UTF-8")
        } catch (e: Exception) {
            decrypted.toString()
        }
    }
}

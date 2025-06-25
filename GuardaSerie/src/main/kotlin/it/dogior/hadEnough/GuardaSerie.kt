package it.dogior.guardaserie

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class GuardoSerie : MainAPI() {
    override var name = "GuardoSerie"
    override var mainUrl = "https://guardoserie.homes"
    override var lang = "it"
    override val hasMainPage = false
    override val hasSearch = true
    override val hasQuickSearch = true

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/search/${query.replace(" ", "%20")}"
        val doc = app.get(url).document

        return doc.select(".Grid .TPost").mapNotNull {
            val title = it.selectFirst(".Title")?.text()?.trim() ?: return@mapNotNull null
            val href = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val poster = it.selectFirst("img")?.attr("src")

            TvSeriesSearchResponse(
                title,
                href,
                this.name,
                TvType.TvSeries,
                poster,
                null,
                null
            )
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1")?.text()?.trim() ?: "Sconosciuto"
        val poster = doc.selectFirst(".Image img")?.attr("src")
        val description = doc.selectFirst(".Description")?.text()
        val episodes = mutableListOf<Episode>()

        doc.select(".Seasons .SeasonAB").forEach { season ->
            val seasonName = season.selectFirst(".Title")?.text()?.trim() ?: ""
            val seasonNumber = Regex("Stagione\\s+(\\d+)").find(seasonName)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1

            season.select(".EPCard a").forEach { ep ->
                val epUrl = ep.attr("href")
                val epTitle = ep.selectFirst(".Title")?.text()?.trim() ?: "Episodio"
                val epNum = Regex("Episodio\\s+(\\d+)").find(epTitle)?.groupValues?.getOrNull(1)?.toIntOrNull()

                episodes.add(
                    Episode(
                        epUrl,
                        epTitle,
                        seasonNumber,
                        epNum
                    )
                )
            }
        }

        return TvSeriesLoadResponse(
            title,
            url,
            this.name,
            TvType.TvSeries,
            episodes.sortedBy { it.season ?: 0 }.reversed(),
            poster,
            null,
            description,
            null,
            null
        )
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val doc = app.get(data).document

        doc.select("iframe").forEach { frame ->
            val src = frame.attr("src")
            if (src.isNotEmpty()) {
                loadExtractor(src, data, subtitleCallback, callback)
            }
        }
    }
}

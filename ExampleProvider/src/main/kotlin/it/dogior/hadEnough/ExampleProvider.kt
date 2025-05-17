package it.dogior.hadEnough

import android.util.Log
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.app
import org.jsoup.nodes.Document


class ExampleProvider : MainAPI() {
    override var mainUrl = ""
    override var name = "Example"
    override val supportedTypes = setOf(TvType.Movie)
    override var lang = "it"
    override val hasMainPage = true

    override val mainPage = mainPageOf(
        mainUrl to "Home",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        TODO("Not yet implemented")
    }

    // this function gets called when you search for something
    override suspend fun search(query: String): List<SearchResponse> {
        TODO("Not yet implemented")
    }

    override suspend fun load(url: String): LoadResponse {
        TODO("Not yet implemented")
    }

    private fun getEpisodes(page: Document): List<Episode> {
        TODO("Not yet implemented")
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        TODO("Not yet implemented")
    }
}
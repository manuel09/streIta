package it.dogior.hadEnough.extractors

import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup

class SuperVideoExtractor : ExtractorApi() {
    override val name = "SuperVideo"
    override val mainUrl = "https://supervideo.tv"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val page = app.get(url).text
        val document = Jsoup.parse(page)
        val script = document.select("script").find { it.data().contains("sources") }?.data() ?: return

        val videoUrl = Regex("""file["']?\s*:\s*["'](https[^"']+\.mp4)["']""")
            .find(script)
            ?.groupValues?.get(1)
            ?: return

        callback.invoke(
            newExtractorLink(
                name = name,
                source = name,
                url = videoUrl,
                type = ExtractorLinkType.MP4
            ) {
                this.referer = referer ?: ""
                this.quality = Qualities.Unknown.value
            }
        )
    }
}

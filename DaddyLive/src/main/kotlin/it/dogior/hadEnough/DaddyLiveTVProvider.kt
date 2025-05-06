package it.dogior.hadEnough

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LiveSearchResponse
import com.lagradost.cloudstream3.LiveStreamLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.VPNStatus
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import java.util.regex.Pattern

class DaddyLiveTVProvider : MainAPI() {
    override var mainUrl = "https://daddylive.dad"
    override var name = "DaddyLive TV"
    override val supportedTypes = setOf(TvType.Live)
    override var lang = "un"
    override val hasMainPage = true
    override val vpnStatus = VPNStatus.MightBeNeeded
    override val hasDownloadSupport = false
    override val instantLinkLoading = true

    private val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36"

    @Suppress("ConstPropertyName")
    companion object {
        val channelsName: MutableMap<String, String> = mutableMapOf()
        private const val poster = "https://raw.githubusercontent.com/doGior/doGiorsHadEnough/refs/heads/master/DaddyLive/daddylive.jpg"
        val countries = listOf(
            "Andorra",
            "UAE",
            "Afghanistan",
            "Antigua and Barbuda",
            "Anguilla",
            "Albania",
            "Armenia",
            "Angola",
            "Antarctica",
            "Argentina",
            "American Samoa",
            "Austria",
            "Australia",
            "Aruba",
            "�land",
            "Azerbaijan",
            "Bosnia and Herzegovina",
            "Barbados",
            "Bangladesh",
            "Belgium",
            "Burkina Faso",
            "Bulgaria",
            "Bahrain",
            "Burundi",
            "Benin",
            "Saint Barth�lemy",
            "Bermuda",
            "Brunei",
            "Bolivia",
            "Bonaire, Sint Eustatius, and Saba",
            "Brasil",
            "Bahamas",
            "Bhutan",
            "Bouvet Island",
            "Botswana",
            "Belarus",
            "Belize",
            "Canada",
            "Cocos (Keeling) Islands",
            "DR Congo",
            "Central African Republic",
            "Congo Republic",
            "Switzerland",
            "Ivory Coast",
            "Cook Islands",
            "Chile",
            "Cameroon",
            "China",
            "Colombia",
            "Costa Rica",
            "Cuba",
            "Cabo Verde",
            "Cura�ao",
            "Christmas Island",
            "Cyprus",
            "Czechia",
            "Germany",
            "Djibouti",
            "Denmark",
            "Dominica",
            "Dominican Republic",
            "Algeria",
            "Ecuador",
            "Estonia",
            "Egypt",
            "Western Sahara",
            "Eritrea",
            "Spain",
            "Ethiopia",
            "Finland",
            "Fiji",
            "Falkland Islands",
            "Micronesia",
            "Faroe Islands",
            "France",
            "Gabon",
            "UK",
            "Grenada",
            "Georgia",
            "French Guiana",
            "Guernsey",
            "Ghana",
            "Gibraltar",
            "Greenland",
            "The Gambia",
            "Guinea",
            "Guadeloupe",
            "Equatorial Guinea",
            "Greece",
            "South Georgia and South Sandwich Islands",
            "Guatemala",
            "Guam",
            "Guinea-Bissau",
            "Guyana",
            "Hong Kong",
            "Heard and McDonald Islands",
            "Honduras",
            "Croatia",
            "Haiti",
            "Hungary",
            "Indonesia",
            "Ireland",
            "Israel",
            "Isle of Man",
            "India",
            "British Indian Ocean Territory",
            "Iraq",
            "Iran",
            "Iceland",
            "Italy",
            "Jersey",
            "Jamaica",
            "Jordan",
            "Japan",
            "Kenya",
            "Kyrgyzstan",
            "Cambodia",
            "Kiribati",
            "Comoros",
            "St Kitts and Nevis",
            "North Korea",
            "South Korea",
            "Kuwait",
            "Cayman Islands",
            "Kazakhstan",
            "Laos",
            "Lebanon",
            "Saint Lucia",
            "Liechtenstein",
            "Sri Lanka",
            "Liberia",
            "Lesotho",
            "Lithuania",
            "Luxembourg",
            "Latvia",
            "Libya",
            "Morocco",
            "Monaco",
            "Moldova",
            "Montenegro",
            "Saint Martin",
            "Madagascar",
            "Marshall Islands",
            "North Macedonia",
            "Mali",
            "Myanmar",
            "Mongolia",
            "Macao",
            "Northern Mariana Islands",
            "Martinique",
            "Mauritania",
            "Montserrat",
            "Malta",
            "Mauritius",
            "Maldives",
            "Malawi",
            "Mexico",
            "Malaysia",
            "Mozambique",
            "Namibia",
            "New Caledonia",
            "Niger",
            "Norfolk Island",
            "Nigeria",
            "Nicaragua",
            "The Netherlands",
            "Norway",
            "Nepal",
            "Nauru",
            "Niue",
            "New Zealand",
            "Oman",
            "Panama",
            "Peru",
            "French Polynesia",
            "Papua New Guinea",
            "Philippines",
            "Pakistan",
            "Poland",
            "Saint Pierre and Miquelon",
            "Pitcairn Islands",
            "Puerto Rico",
            "Palestine",
            "Portugal",
            "Palau",
            "Paraguay",
            "Qatar",
            "R�union",
            "Romania",
            "Serbia",
            "Russia",
            "Rwanda",
            "Saudi Arabia",
            "Solomon Islands",
            "Seychelles",
            "Sudan",
            "Sweden",
            "Singapore",
            "Saint Helena",
            "Slovenia",
            "Svalbard and Jan Mayen",
            "Slovakia",
            "Sierra Leone",
            "San Marino",
            "Senegal",
            "Somalia",
            "Suriname",
            "South Sudan",
            "S�o Tom� and Pr�ncipe",
            "El Salvador",
            "Sint Maarten",
            "Syria",
            "Eswatini",
            "Turks and Caicos Islands",
            "Chad",
            "French Southern Territories",
            "Togo",
            "Thailand",
            "Tajikistan",
            "Tokelau",
            "Timor-Leste",
            "Turkmenistan",
            "Tunisia",
            "Tonga",
            "Turkey",
            "Trinidad and Tobago",
            "Tuvalu",
            "Taiwan",
            "Tanzania",
            "Ukraine",
            "Uganda",
            "U.S. Outlying Islands",
            "USA",
            "Uruguay",
            "Uzbekistan",
            "Vatican City",
            "St Vincent and Grenadines",
            "Venezuela",
            "British Virgin Islands",
            "U.S. Virgin Islands",
            "Vietnam",
            "Vanuatu",
            "Wallis and Futuna",
            "Samoa",
            "Kosovo",
            "Yemen",
            "Mayotte",
            "South Africa",
            "Zambia",
            "Zimbabwe",
        )
    }

    private suspend fun searchResponseBuilder(): List<LiveSearchResponse> {
        val channelsUrl = "$mainUrl/24-7-channels.php"
        val response = app.post(
            channelsUrl,
            headers = mapOf(
                "Referer" to mainUrl,
                "User-Agent" to userAgent
            )
        )
        val respBody = response.body.string()
        val chBlockPattern =
            Pattern.compile("<center><h1(.+?)tab-2", Pattern.DOTALL or Pattern.MULTILINE)
        val chBlockMatcher = chBlockPattern.matcher(respBody)
        val chBlock = if (chBlockMatcher.find()) chBlockMatcher.group(1) else ""

        val chanDataPattern = Pattern.compile("href=\"(.*)\" target(.*)<strong>(.*)</strong>")
        val chanDataMatcher = chanDataPattern.matcher(chBlock)
        val chanData = mutableListOf<List<String>>()

        while (chanDataMatcher.find()) {
            val href = chanDataMatcher.group(1)
            val target = chanDataMatcher.group(2)
            val strongText = chanDataMatcher.group(3)
            chanData.add(listOf(href, target, strongText) as List<String>)
        }

        return chanData.map {
            val name = it[2]
            val url = it[0]
            channelsName["$mainUrl$url"] = name
            newLiveSearchResponse(name, url){
                posterUrl = poster
            }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val searchResponses = searchResponseBuilder()
        val groupedSearchResponses = searchResponses.groupBy {
            val c = it.name.substringAfterLast(" ")
                .replace(")", "").trim()
            if (countries.any { country -> country.lowercase() in c.lowercase() }) {
                c
            } else {
                "Unknown"
            }
        }
        val sections = groupedSearchResponses.map {
            HomePageList(
                it.key,
                it.value,
                false
            )
        }.sortedBy { it.name }
        return newHomePageResponse(
            sections,
            false
        )
    }

    // this function gets called when you search for something
    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponses = searchResponseBuilder()
        val matches = searchResponses.filter {
            query.lowercase().replace(" ", "") in
                    it.name.lowercase().replace(" ", "")
        }
        return matches
    }

    override suspend fun load(url: String): LoadResponse {
        return newLiveStreamLoadResponse(channelsName[url] ?: "Channel",
            url, url){
            posterUrl = poster
        }
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        return loadExtractor(data, null, subtitleCallback, callback)
    }
}
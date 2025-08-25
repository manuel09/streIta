// use an integer for version numbers
version = 2


cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "They added cloudflare 😔. Movies and Tv Series from Altadefinizione"
    authors = listOf("doGior")

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 0

    tvTypes = listOf("Movie", "TvSeries", "Documentary")

    requiresResources = false
    language = "it"

    iconUrl = "https://altadefinizione.free/templates/Alta/images/favicon/apple-touch-icon.png"
}

// use an integer for version numbers
version = 1


cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "Documentaries from arte.tv. Other languages will be implemented soon"
    authors = listOf("doGior")

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 1

    tvTypes = listOf("Documentary")

    requiresResources = false
    language = "it"

    iconUrl = "https://static-cdn.arte.tv/replay/favicons/favicon-194x194.png"
}

version = 2

cloudstream {
    description = "Cartoni animati in streaming ITA da loonex.eu"
    authors = listOf("doGior", "streIta")

    status = 1

    tvTypes = listOf(
        "Cartoon",
        "TvSeries",
        "Movie",
        "Anime"
    )

    language = "it"
    iconUrl = "https://loonex.eu/archivio-cartoni-logo.png"
}

dependencies {
    implementation("com.google.android.material:material:1.12.0")
}

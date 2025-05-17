package it.dogior.hadEnough

import com.fasterxml.jackson.annotation.JsonProperty

class MovieDetail(
    @JsonProperty("actor")
    val actor: List<Actor>,
    @JsonProperty("aggregateRating")
    val aggregateRating: AggregateRating,
    @JsonProperty("@context")
    val context: String,
    @JsonProperty("datePublished")
    val datePublished: String,
    @JsonProperty("description")
    val description: String,
    @JsonProperty("director")
    val director: List<Director>,
    @JsonProperty("genre")
    val genre: List<String>,
    @JsonProperty("image")
    val image: String,
    @JsonProperty("name")
    val name: String,
    @JsonProperty("@type")
    val type: String,
    @JsonProperty("url")
    val url: String
)

data class Actor(
    @JsonProperty("name")
    val name: String,
    @JsonProperty("@type")
    val type: String
)

data class AggregateRating(
    @JsonProperty("bestRating")
    val bestRating: String,
    @JsonProperty("ratingValue")
    val ratingValue: String,
    @JsonProperty("reviewCount")
    val reviewCount: String,
    @JsonProperty("@type")
    val type: String,
    @JsonProperty("worstRating")
    val worstRating: String
)

data class Director(
    @JsonProperty("name")
    val name: String,
    @JsonProperty("@type")
    val type: String
)



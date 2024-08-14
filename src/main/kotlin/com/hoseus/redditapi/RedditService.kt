package com.hoseus.redditapi

import io.smallrye.mutiny.Uni
import io.vertx.mutiny.ext.web.client.WebClient
import jakarta.enterprise.context.ApplicationScoped

//https://support.reddithelp.com/hc/en-us/articles/16160319875092-Reddit-Data-API-Wiki
@ApplicationScoped
class RedditService(
    private val restClient: WebClient
) {

    fun getPost(postUrl: String): Uni<RedditPost> =
        this.restClient
            .getAbs("$postUrl.json")
            .send()
            .map {
                it
                    .bodyAsJsonArray()
                    .getJsonObject(0)
                    .getJsonObject("data")
                    .getJsonArray("children")
                    .getJsonObject(0)
                    .getJsonObject("data")
            }
            .map {
                RedditPost(
                    subreddit = it.getString("subreddit"),
                    author = "u/${it.getString("author")}",
                    title = it.getString("title"),
                    text = it.getString("selftext"),
                    url = it.getString("url")
                )
            }
}
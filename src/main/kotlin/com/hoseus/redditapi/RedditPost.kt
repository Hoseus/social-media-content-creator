package com.hoseus.redditapi

data class RedditPost (
    val subreddit: String,
    val author: String,
    val title: String,
    val text: String,
    val url: String
)
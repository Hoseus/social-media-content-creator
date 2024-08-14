package com.hoseus.redditapi

import com.fasterxml.jackson.annotation.JsonProperty
import com.hoseus.tts.SpeakerSex

data class CreateContentDto (
    @JsonProperty("text")
    val text: String,
    @JsonProperty("speaker_sex")
    val speakerSex: SpeakerSex
)

data class CreateRedditDramaContentDto (
    @JsonProperty("post_url")
    val postUrl: String,
    @JsonProperty("speaker_sex")
    val speakerSex: SpeakerSex
)

data class SuccessResponse (
    val status: Status
)

data class ErrorResponse (
    val status: Status,
    val message: String
)

enum class Status {
    SUCCESS,
    FAILURE
}
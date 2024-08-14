package com.hoseus.redditapi

import com.fasterxml.jackson.annotation.JsonProperty
import com.hoseus.tts.SpeakerSex
import io.quarkus.runtime.annotations.RegisterForReflection

@RegisterForReflection
data class CreateContentDto (
    @JsonProperty("text")
    val text: String,
    @JsonProperty("speaker_sex")
    val speakerSex: SpeakerSex
)

@RegisterForReflection
data class CreateRedditDramaContentDto (
    @JsonProperty("post_url")
    val postUrl: String,
    @JsonProperty("speaker_sex")
    val speakerSex: SpeakerSex
)

@RegisterForReflection
data class SuccessResponse (
    val status: Status
)

@RegisterForReflection
data class ErrorResponse (
    val status: Status,
    val message: String
)

@RegisterForReflection
enum class Status {
    SUCCESS,
    FAILURE
}
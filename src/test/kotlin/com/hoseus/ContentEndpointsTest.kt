package com.hoseus

import com.hoseus.redditapi.CreateContentDto
import com.hoseus.redditapi.CreateRedditDramaContentDto
import com.hoseus.tts.SpeakerSex
import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import io.restassured.config.HttpClientConfig
import io.restassured.config.RestAssuredConfig
import io.restassured.http.ContentType
import org.hamcrest.CoreMatchers.`is`
import org.junit.jupiter.api.Test

@QuarkusTest
class ContentEndpointsTest {
    private val restAssuredConfig =
        RestAssuredConfig
            .config()
            .httpClient(
                HttpClientConfig
                    .httpClientConfig()
                    .setParam("http.connection.timeout", 600000)
            )

    @Test
    fun contentEndpointTest() {
        val body = CreateContentDto(
            text = """Hi
                |I want to be okay with how I look, and how tall I am. Sometimes I feel like a smushed and burned grilled cheese sandwich""".trimMargin(),
            speakerSex = SpeakerSex.F
        )

        val expectedResponse = """{"status":"SUCCESS"}"""

        given()
            .config(this.restAssuredConfig)
            .and()
            .contentType(ContentType.JSON)
            .and()
            .body(body)
            .`when`().post("/content")
            .then()
            .statusCode(200)
            .body(`is`(expectedResponse))
    }

    @Test
    fun contentRedditDramaEndpointTest() {
        val body = CreateRedditDramaContentDto(
            postUrl = """https://www.reddit.com/r/short/comments/1gegx75/hi/""",
            speakerSex = SpeakerSex.F
        )

        val expectedResponse = """{"status":"SUCCESS"}"""

        given()
            .config(this.restAssuredConfig)
            .and()
            .contentType(ContentType.JSON)
            .and()
            .body(body)
            .`when`().post("/content/reddit-drama")
            .then()
            .statusCode(200)
            .body(`is`(expectedResponse))
    }

}
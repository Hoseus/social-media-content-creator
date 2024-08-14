package com.hoseus

import com.hoseus.content.ContentService
import com.hoseus.tts.SpeakerSex
import io.quarkus.test.junit.QuarkusTest
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.io.path.exists
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import kotlin.time.toJavaDuration

@QuarkusTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContentServiceTest {
    @Inject
    lateinit var contentService: ContentService

    private val subscriptionTimeout = 10.toDuration(DurationUnit.MINUTES).toJavaDuration()

    @Test
    fun createFromRedditDramaTest() {
        this.contentService.createFromRedditDrama("https://www.reddit.com/r/short/comments/1gegx75/hi/", SpeakerSex.F)
            .invoke { it -> Assertions.assertTrue(it.exists()) }
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem(this.subscriptionTimeout)
            .assertCompleted()
    }

    @Test
    fun createFromTextTest() {
        this.contentService.createFromText(
            """I want to be okay with how I look, and how tall I am. Sometimes I feel like a smushed and burned grilled cheese sandwich""",
            SpeakerSex.M
        )
        .invoke { it -> Assertions.assertTrue(it.exists()) }
        .subscribe().withSubscriber(UniAssertSubscriber.create())
        .awaitItem(this.subscriptionTimeout)
        .assertCompleted()
    }
}
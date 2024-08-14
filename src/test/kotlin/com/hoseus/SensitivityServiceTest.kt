package com.hoseus

import com.hoseus.sensitivity.DefaultSensitivityService
import com.hoseus.sensitivity.NoOpSensitivityService
import io.quarkus.test.junit.QuarkusTest
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber
import jakarta.inject.Inject
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@QuarkusTest
class SensitivityServiceTest {
    @Inject
    private lateinit var defaultSensitivityService: DefaultSensitivityService

    @Inject
    private lateinit var noOpSensitivityService: NoOpSensitivityService

    @Test
    fun defaultObfuscationTest() {
        val text = """
            |kill killed murder murdered suicide suicided death die died sex sexual sexually sexualize sexualized
            |rape raped assault assaulted lesbian homosexual gay trans transexual
            |""".trimMargin().replace('\n', ' ').trim()

        val expectedResult = """
            |person-delete person-deleted person-delete person-deleted self-delete self-deleted life-delete
            |life-delete life-deleted tiranosaurus-seggs seggsual seggsually seggsualize seggsualized grape
            |graped as-salt as-salted raibow-flag hormone-seggsual raibow-flag trains train-seggsual
            |""".trimMargin().replace('\n', ' ').trim()

        this.defaultSensitivityService.obfuscateSensitiveWords(text)
            .invoke { it -> Assertions.assertEquals(expectedResult, it) }
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .assertCompleted()
    }

    @Test
    fun defaultIgnoringNormalWordsTest() {
        val text = """
            |I went to a flower garden
            |""".trimMargin().replace('\n', ' ').trim()

        this.defaultSensitivityService.obfuscateSensitiveWords(text)
            .invoke { it -> Assertions.assertEquals(text, it) }
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .assertCompleted()
    }

    @Test
    fun defaultObfuscationEmptyStringTest() {
        val text = ""

        this.defaultSensitivityService.obfuscateSensitiveWords(text)
            .invoke { it -> Assertions.assertEquals(text, it) }
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .assertCompleted()
    }

    @Test
    fun noOpObfuscationTest() {
        val text = """
            |kill killed murder murdered suicide suicided death die died sex sexual sexually sexualize sexualized
            |rape raped assault assaulted lesbian homosexual gay trans transexual
            |""".trimMargin().replace('\n', ' ').trim()

        this.noOpSensitivityService.obfuscateSensitiveWords(text)
            .invoke { it -> Assertions.assertEquals(text, it) }
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .assertCompleted()
    }

    @Test
    fun noOpIgnoringNormalWordsTest() {
        val text = """
            |I went to a flower garden
            |""".trimMargin().replace('\n', ' ').trim()

        this.noOpSensitivityService.obfuscateSensitiveWords(text)
            .invoke { it -> Assertions.assertEquals(text, it) }
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .assertCompleted()
    }

    @Test
    fun noOpObfuscationEmptyStringTest() {
        val text = ""

        this.noOpSensitivityService.obfuscateSensitiveWords(text)
            .invoke { it -> Assertions.assertEquals(text, it) }
            .subscribe().withSubscriber(UniAssertSubscriber.create())
            .awaitItem()
            .assertCompleted()
    }
}
package com.blockbuster.plugin.roku.ecp

import com.blockbuster.media.RokuMediaContent
import com.blockbuster.media.RokuMediaMetadata
import com.blockbuster.plugin.roku.RokuAction
import com.blockbuster.plugin.roku.RokuPlaybackCommand
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * Validates the generated [RokuEcp] core against the roku-deeplink spec fixtures
 * (`test_fixtures.json`, copied from the spec-library at v1.3.0). This is the
 * speclib trust gate: the generated code must satisfy every spec fixture.
 */
class RokuEcpFixtureTest {
    private val mapper = ObjectMapper()

    private val fixtures: JsonNode =
        requireNotNull(javaClass.getResourceAsStream("/roku-deeplink/test_fixtures.json")) {
            "missing test fixtures resource"
        }.use { mapper.readTree(it) }

    @TestFactory
    fun `valid URLs extract to the expected content`(): List<DynamicTest> =
        fixtures["valid_urls"].map { case ->
            val url = case["url"].asText()
            DynamicTest.dynamicTest(url) {
                val expected = case["expected"]
                val got = requireNotNull(RokuEcp.convertUrlToEcpCommand(url)) { "expected a match for $url" }
                assertEquals(expected["channel_id"].asText(), got.channelId)
                assertEquals(expected["channel_name"].asText(), got.channelName)
                assertEquals(expected["content_id"].asText(), got.contentId)
                assertEquals(expected["media_type"].asText(), got.mediaType)
            }
        }

    @TestFactory
    fun `invalid URLs return null`(): List<DynamicTest> =
        fixtures["invalid_urls"].map { case ->
            val url = case["url"].asText()
            DynamicTest.dynamicTest(url) {
                assertNull(RokuEcp.convertUrlToEcpCommand(url), "expected null for $url")
            }
        }

    @TestFactory
    fun `descriptors build the expected playback command`(): List<DynamicTest> =
        fixtures["playback_commands"].mapIndexed { index, case ->
            val input = case["input"]
            DynamicTest.dynamicTest("${input["channel_name"].asText()} #$index") {
                val content = contentFrom(input)
                val command = RokuEcp.buildPlaybackCommand(content)
                assertEquals(expectedActions(case["expected"]["actions"]), actualActions(command))
            }
        }

    private fun contentFrom(input: JsonNode): RokuMediaContent =
        RokuMediaContent(
            channelName = input["channel_name"].asText(),
            channelId = input["channel_id"].asText(),
            contentId = input["content_id"].asText(),
            mediaType = input["media_type"]?.asText(),
            metadata =
                input["resume_position_ticks"]?.let {
                    RokuMediaMetadata(resumePositionTicks = it.asLong())
                },
        )

    private fun actualActions(command: RokuPlaybackCommand): List<String> {
        val sequence = assertInstanceOf(RokuPlaybackCommand.ActionSequence::class.java, command)
        return sequence.actions.map { action ->
            when (action) {
                is RokuAction.Launch -> "launch:${action.channelId}:${action.params}"
                is RokuAction.Wait -> "wait:${action.milliseconds}"
                is RokuAction.Press -> "keypress:${action.key.ecpName}:${action.count}"
                is RokuAction.Type -> "type:${action.text}"
            }
        }
    }

    private fun expectedActions(actions: JsonNode): List<String> =
        actions.map { action ->
            when (val type = action["type"].asText()) {
                "launch" -> "launch:${action["channel_id"].asText()}:${action["params"].asText()}"
                "wait" -> "wait:${action["milliseconds"].asLong()}"
                "keypress" -> "keypress:${action["key"].asText()}:${action["count"].asInt()}"
                else -> error("unknown action type: $type")
            }
        }
}

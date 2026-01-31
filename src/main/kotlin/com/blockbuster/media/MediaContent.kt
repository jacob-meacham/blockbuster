package com.blockbuster.media

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

interface MediaContent {
    fun toJson(): String
}

interface MediaContentParser<T : MediaContent> {
    fun fromJson(json: String): T
}

object MediaJson {
    val mapper: ObjectMapper = jacksonObjectMapper()
        .registerModule(KotlinModule.Builder().build())
        .registerModule(JavaTimeModule())
}



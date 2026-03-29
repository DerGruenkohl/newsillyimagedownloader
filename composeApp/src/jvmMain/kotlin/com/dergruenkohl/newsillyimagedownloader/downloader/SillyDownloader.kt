package com.dergruenkohl.newsillyimagedownloader.downloader

import com.dergruenkohl.newsillyimagedownloader.data.DatabaseService

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache5.Apache5
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.seconds

class R34Downloader(
    val client: HttpClient,
    val databaseService: DatabaseService? = null,
    val tags: String? = null,
    val maxPage: Int = 242
) {
    val logger = KotlinLogging.logger {}
    val idRegex = Regex("[?&]id=(\\d+)")
    val increment = 24


}

package com.dergruenkohl.newsillyimagedownloader.downloader

import com.dergruenkohl.newsillyimagedownloader.data.DatabaseService
import com.dergruenkohl.newsillyimagedownloader.data.Femboy
import com.dergruenkohl.newsillyimagedownloader.data.Rating

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.parameters
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.seconds

class R34Downloader(
    override val client: HttpClient,
    override val databaseService: DatabaseService,
    override val tags: String,
    override val maxPage: Int = 242
): MetadataDownloader {
    val logger = KotlinLogging.logger {}
    val idRegex = Regex("[?&]id=(\\d+)")
    val increment = 24
    val apiKey = "9dcf24d9b2f138c728c32c087579a3623f228cdd7ad36302a3418ba2ea1f67269909c5bf02830dc6db7189ba7c28ffc435ebddd97d2d1a58a93a9e08c5fded88"
    val userID = "6040033"

    @Serializable
    data class R34Object(
        @SerialName("file_url")
        val fileUrl: String,
        val tags: String,
        val id: Long
    ){
        val logger = KotlinLogging.logger {}
        fun toFemboy(): Femboy {
            return Femboy(
                id = "101$id".toLong(),
                tags = tags.split(" ").map { it.trim() },
                rating = Rating.NSFW,
                fileUrl = fileUrl,
                names = tags.split(" ").filter { it.endsWith("(umamusume)") }
            )
        }
    }

    override suspend fun fetchMetadata() {
        logger.info{"tags: $tags"}
        for (page in 0..maxPage){
            val results = client.get("https://api.rule34.xxx/index.php") {
                url {
                    encodedParameters.append("tags", tags)
                }

                parameter("page", "dapi")
                parameter("s","post")
                parameter("q","index")
                parameter("json", "1")
                parameter("user_id",userID)
                parameter("api_key", apiKey)
                parameter("limit", "1000")
                parameter("pid", page)
            }.body<List<R34Object>>()
            logger.info { "Fetched ${results.size} results from API" }
            databaseService.insertFemboys(results.map { it.toFemboy() }.filterNot { it.tags.contains("ai_generated") })
            delay(1.seconds)
        }
    }


}

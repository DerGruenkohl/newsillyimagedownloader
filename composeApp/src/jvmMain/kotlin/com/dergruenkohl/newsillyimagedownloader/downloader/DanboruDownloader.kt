package com.dergruenkohl.newsillyimagedownloader.downloader

import com.dergruenkohl.newsillyimagedownloader.data.DatabaseService
import com.dergruenkohl.newsillyimagedownloader.data.Femboy
import com.dergruenkohl.newsillyimagedownloader.data.Rating
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.seconds

class DanboruDownloader(
    val client: HttpClient,
    val databaseService: DatabaseService,
    val tag1: String,
    val tag2: String? = null
) {
    private val logger = KotlinLogging.logger {}
    val danbooruUrl = "https://danbooru.donmai.us/posts.json?tags=astolfo_(fate)&page={page}&limit=200"
    val maxPage = 35
    @Serializable
    data class DanbooruPost(
        val id: Int,
        @SerialName("tag_string")
        val tags: String,
        val rating: String,
        @SerialName("file_url")
        val fileUrl: String? = null,
        @SerialName("tag_string_character")
        val name: String
    ){
        fun toFemboy(): Femboy? {
            if (fileUrl.isNullOrEmpty()) {
                return null
            }
            return Femboy(
                id = id,
                tags = tags.split(" "),
                rating = Rating.Companion.fromString(rating),
                fileUrl = fileUrl,
                names = name.split(" ")
            )
        }
    }
     suspend fun getFemboyMetadata() {
        for (page in 1..maxPage) {
            try {
                val url = danbooruUrl.replace("{page}", page.toString())
                val response: List<DanbooruPost> = client.get(url).body()
                logger.debug { "Got ${response.size} posts from page $page" }
                val femboys = response.mapNotNull { it.toFemboy() }
                logger.debug { "Converted ${femboys.size} posts to Femboy metadata" }
                databaseService.insertFemboys(femboys)
                logger.debug { "Appended metadata for ${femboys.size} femboys to database" }
                delay(0.3.seconds)
            } catch (e: Exception) {
                logger.error(e) { "Error while getting posts from page $page" }
            }
        }
    }
}
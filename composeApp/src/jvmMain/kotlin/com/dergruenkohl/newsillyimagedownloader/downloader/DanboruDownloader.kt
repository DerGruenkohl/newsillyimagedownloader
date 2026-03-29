package com.dergruenkohl.newsillyimagedownloader.downloader

import com.dergruenkohl.newsillyimagedownloader.data.DatabaseService
import com.dergruenkohl.newsillyimagedownloader.data.Femboy
import com.dergruenkohl.newsillyimagedownloader.data.Rating
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.seconds

class DanboruDownloader(
    val client: HttpClient,
    val databaseService: DatabaseService,
    val tags: String,
    val maxPage: Int
) {
    private val logger = KotlinLogging.logger {}
    val danbooruUrl = "https://danbooru.donmai.us/posts.json?tags=$tags&page={page}&limit=200"
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
         coroutineScope {
             val channel = Channel<String>(capacity = 16)
             val workers = List(8) {
                 async {
                     for (url in channel) {
                         logger.info { "Worker $it downloading: $url" }
                         try {
                             val response: List<DanbooruPost> = client.get(url).body()
                             logger.info { "Got ${response.size} posts on worker $it" }
                             val femboys = response.mapNotNull { it.toFemboy() }
                             logger.info { "Converted ${femboys.size} posts to Femboy metadata" }
                             databaseService.insertFemboys(femboys)
                             logger.info { "Appended metadata for ${femboys.size} femboys to database" }
                             delay(0.3.seconds)
                         } catch (e: Exception) {
                             logger.error(e) { "Error while getting posts from url $url" }
                         }
                     }
                 }
             }
             val producer = async {
                    try {
                        for (page in 1..maxPage) {
                            logger.info { "Feeding page $page" }
                            val url = danbooruUrl.replace("{page}", page.toString())
                            channel.send(url)
                        }
                    } catch (e: Exception) {
                        logger.error { e }
                    } finally {
                        channel.close()
                    }
             }
             producer.await()
             workers.awaitAll()
         }

//        for (page in 1..maxPage) {
//            try {
//                val url = danbooruUrl.replace("{page}", page.toString())
//                val response: List<DanbooruPost> = client.get(url).body()
//                logger.info { "Got ${response.size} posts from page $page" }
//                val femboys = response.mapNotNull { it.toFemboy() }
//                logger.info { "Converted ${femboys.size} posts to Femboy metadata" }
//                databaseService.insertFemboys(femboys)
//                logger.info { "Appended metadata for ${femboys.size} femboys to database" }
//                delay(0.1.seconds)
//            } catch (e: Exception) {
//                logger.error(e) { "Error while getting posts from page $page" }
//            }
//        }
    }
}
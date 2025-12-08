package com.dergruenkohl.newsillyimagedownloader.downloader

import com.dergruenkohl.newsillyimagedownloader.data.DatabaseService
import com.dergruenkohl.newsillyimagedownloader.data.Femboy
import com.dergruenkohl.newsillyimagedownloader.data.Rating
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess
import kotlin.io.path.Path

class ImageDownloader(
    val database: DatabaseService,
    val client: HttpClient,
    val tags: List<String>,
    basePath: String
)
{
    val logger = KotlinLogging.logger {}
    val basePath = Path(basePath)
    var totalDownloaded = 0
    var totalImages = 0
    var errors = 0
    suspend fun downloadImages() {
        val femboys = database.getFemboysWithTag(tags)
        femboys.forEach { femboy ->
            try {
                logger.info { "Downloading image ${femboy.id}" }
                downloadImage(femboy)
            } catch (e: Exception) {
                logger.error(e) { "Error downloading image ${femboy.id}" }
                errors++
            }
        }
    }
    suspend fun downloadImage(femboy: Femboy) {
        val response = client.get(femboy.fileUrl)
        if(!response.status.isSuccess()){
            logger.warn { "Error downloading $femboy: ${response.status}" }
            errors++
            return
        }
        val extension = femboy.fileUrl.substringAfterLast(".")
        val file = when(femboy.rating){
            Rating.SFW -> basePath.resolve("sfw/${femboy.id}.$extension").toFile()
            Rating.NSFW -> basePath.resolve("nsfw/${femboy.id}.$extension").toFile()
            Rating.QUESTIONABLE -> basePath.resolve("questionable/${femboy.id}.$extension").toFile()
        }
        val bytes = response.bodyAsBytes()
        file.writeBytes(bytes)
        totalDownloaded++
        logger.info { "Downloaded image ${femboy.id} to ${file.path}"}
    }
}
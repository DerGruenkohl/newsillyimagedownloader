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
import kotlin.io.path.exists
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

class ImageDownloader(
        val database: DatabaseService,
        val client: HttpClient,
        val tags: List<String>,
        basePath: String,
        val controller: DownloadController? = null,
        val concurrency: Int = 8,
        val split: Boolean,
        val whiteListedTags: List<String>,
) {
    val logger = KotlinLogging.logger {}
    val basePath = Path(basePath)
    var totalDownloaded = 0
    var totalImages = 0
    var errors = 0

    suspend fun downloadImages() =
            withContext(Dispatchers.IO) {
                val femboys = database.getFemboysWithTag(tags)
                totalImages = femboys.size

                femboys.chunked(concurrency).forEach { chunk ->
                    // check for pause/stop before starting next chunk
                    controller?.checkPausedOrStopped()
                    coroutineScope {
                        chunk
                                .map { femboy ->
                                    async {
                                        try {
                                            controller?.checkPausedOrStopped()
                                            logger.info { "Downloading image ${femboy.id}" }
                                            downloadImage(femboy)
                                        } catch (e: CancellationException) {
                                            logger.info { "Download cancelled for ${femboy.id}" }
                                        } catch (e: Exception) {
                                            logger.error(e) {
                                                "Error downloading image ${femboy.id}"
                                            }
                                            errors++
                                        }
                                    }
                                }
                                .awaitAll()
                    }
                }
            }

    suspend fun downloadImage(femboy: Femboy) {
        controller?.checkPausedOrStopped()

        val extension = femboy.fileUrl.substringAfterLast(".")
        val paths =
                if (split) {
                    femboy.names
                        .filter { name ->
                            if (whiteListedTags.isEmpty()){
                                return@filter true
                            }
                            whiteListedTags.any { wl -> name.contains(wl, ignoreCase = true) }
                        }
                        .map { basePath.resolve(it.replace("/", "_")) }
                } else {
                    listOf(basePath)
                }

        val files =
                paths
                        .map {
                            it.resolve("${femboy.rating.name.lowercase()}/${femboy.id}.$extension")
                                    .toFile()
                        }
                        .filter { !it.exists() }
        val file =
                when (femboy.rating) {
                    Rating.SFW -> basePath.resolve("sfw/${femboy.id}.$extension").toFile()
                    Rating.NSFW -> basePath.resolve("nsfw/${femboy.id}.$extension").toFile()
                    Rating.QUESTIONABLE ->
                            basePath.resolve("questionable/${femboy.id}.$extension").toFile()
                }
        if (file.exists()) {
            logger.info { "Image ${femboy.id} already exists, skipping download" }
            return
        }
        if (files.isEmpty()) {
            logger.info { "No images to download for ${femboy.id}, skipping download " }
            return
        }

        controller?.checkPausedOrStopped()
        val response = client.get(femboy.fileUrl)
        if (!response.status.isSuccess()) {
            logger.warn { "Error downloading $femboy: ${response.status}" }
            errors++
            return
        }
        val bytes = response.bodyAsBytes()
        files.forEach {
            it.parentFile.mkdirs()
            it.writeBytes(bytes)
            logger.info { "Downloaded image ${femboy.id} to ${it.path}" }
        }
        // file.writeBytes(bytes)
        totalDownloaded++
    }
}

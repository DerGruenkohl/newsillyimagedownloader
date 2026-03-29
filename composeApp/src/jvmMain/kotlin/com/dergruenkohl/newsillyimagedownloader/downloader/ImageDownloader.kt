package com.dergruenkohl.newsillyimagedownloader.downloader

import com.dergruenkohl.newsillyimagedownloader.data.DatabaseService
import com.dergruenkohl.newsillyimagedownloader.data.Femboy
import com.dergruenkohl.newsillyimagedownloader.data.Rating
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.copyTo
import java.nio.channels.Channels
import kotlin.io.path.Path
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel


class ImageDownloader(
        val database: DatabaseService,
        val client: HttpClient,
        val tags: List<String>,
        basePath: String,
        val controller: DownloadController? = null,
        val concurrency: Int = 16,
        val split: Boolean,
        val whiteListedTags: List<String>,
        val onProgressUpdate: (String) -> Unit
) {
    val logger = KotlinLogging.logger {}
    val basePath = Path(basePath)
    val totalDownloaded = AtomicInteger(0)
    val totalProcessed = AtomicInteger(0)
    var totalImages = 0
    val errors = AtomicInteger(0)
    val totalFilesCreated = AtomicInteger(0)


    suspend fun downloadImages() =
        withContext(Dispatchers.IO.limitedParallelism(256)) {
            database.cleanUpDatabase()

            val femboys = database.getFemboysWithTag(tags)
            totalImages = femboys.size

            coroutineScope {
                // Create concurrent worker coroutines
                val channel = Channel<Femboy>(capacity = concurrency * 2)

                // Launch worker coroutines
                val workers = List(concurrency) {
                    async {
                        for (femboy in channel) {
                            try {
                                controller?.checkPausedOrStopped()
                                logger.info { "Downloading image ${femboy.id}" }
                                downloadImage(femboy)
                            } catch (e: CancellationException) {
                                logger.info { "Download cancelled for ${femboy.id}" }
                                throw e
                            } catch (e: Exception) {
                                logger.error(e) { "Error downloading image ${femboy.id}" }
                                errors.incrementAndGet()
                            }
                        }
                    }
                }

                // Producer: feed femboys to workers
                val producer = async {
                    try {
                        for (femboy in femboys) {
                            controller?.checkPausedOrStopped()

                            // Update progress
                            val progressText = """
                            Processed: ${totalProcessed.get()} / $totalImages
                            Downloaded: ${totalDownloaded.get()}
                            Skipped: ${totalProcessed.get() - totalDownloaded.get() - errors.get()}
                            FilesCreated: ${totalFilesCreated.get()}
                            Progress: ${"%.2f".format(totalProcessed.get().toDouble() / totalImages.toDouble() * 100)} %
                            Errors: ${errors.get()}
                        """.trimIndent()
                            withContext(Dispatchers.Main) { onProgressUpdate(progressText) }

                            channel.send(femboy)
                        }
                    } finally {
                        channel.close()
                    }
                }

                // Wait for completion
                producer.await()
                workers.awaitAll()
            }
            // Final progress update
            val progressText = """
            Processed: ${totalProcessed.get()} / $totalImages
            Downloaded: ${totalDownloaded.get()}
            Skipped: ${totalProcessed.get() - totalDownloaded.get() - errors.get()}
            Progress: 100.00 %
            Errors: ${errors.get()}
        """.trimIndent()
            withContext(Dispatchers.Main) { onProgressUpdate(progressText) }
        }


    suspend fun downloadImage(image: Femboy) {
        controller?.checkPausedOrStopped()

        val extension = image.fileUrl.substringAfterLast(".")

        // The canonical file in the base rating folder
        val file =
                when (image.rating) {
                    Rating.SFW -> basePath.resolve("sfw/${image.id}.$extension").toFile()
                    Rating.NSFW -> basePath.resolve("nsfw/${image.id}.$extension").toFile()
                    Rating.QUESTIONABLE ->
                            basePath.resolve("questionable/${image.id}.$extension").toFile()
                }

        totalProcessed.incrementAndGet()

        // Tag-specific link targets (only relevant when split is enabled)
        val linkTargets =
                if (split) {
                    image.names
                        .filter { name ->
                            if (whiteListedTags.isEmpty()) return@filter true
                            whiteListedTags.any { wl -> name.contains(wl, ignoreCase = true) }
                        }
                        .map { name ->
                            basePath
                                .resolve(name.replace("/", "_"))
                                .resolve("${image.rating.name.lowercase()}/${image.id}.$extension")
                                .toFile()
                        }
                        .filter { !it.exists() }
                } else {
                    emptyList()
                }

        if (file.exists()) {
            logger.info { "Image ${image.id} already exists, skipping" }
            return
        }

        if (!split && file.exists()) {
            logger.info { "Image ${image.id} already exists, skipping download" }
            return
        }

        controller?.checkPausedOrStopped()
        val response = client.get(image.fileUrl)
        if (!response.status.isSuccess()) {
            logger.warn { "Error downloading $image: ${response.status}" }
            errors.incrementAndGet()
            return
        }
        // Stream directly to disk — no full-image ByteArray held in memory
        file.parentFile.mkdirs()
        file.outputStream().use { fos ->
            val body: ByteReadChannel = response.bodyAsChannel()
            body.copyTo(Channels.newChannel(fos))
        }
        logger.info { "Downloaded image ${image.id} to ${file.path}" }
        totalFilesCreated.incrementAndGet()
        totalDownloaded.incrementAndGet()

        // Create hard links (or symlinks as fallback) in tag-specific folders
        linkTargets.forEach { link ->
            createLink(link, file)
        }
    }

    private fun createLink(link: java.io.File, target: java.io.File) {
        try {
            link.parentFile.mkdirs()
            Files.createLink(link.toPath(), target.toPath())
            logger.info { "Hard-linked ${target.path} -> ${link.path}" }
            totalFilesCreated.incrementAndGet()
        } catch (_: Exception) {
            // Fall back to symbolic link (e.g. cross-device or unsupported FS)
            try {
                Files.createSymbolicLink(link.toPath(), target.toPath())
                logger.info { "Symlinked ${target.path} -> ${link.path}" }
                totalFilesCreated.incrementAndGet()
            } catch (se: Exception) {
                logger.error(se) { "Failed to create link for ${link.path}" }
            }
        }
    }
}

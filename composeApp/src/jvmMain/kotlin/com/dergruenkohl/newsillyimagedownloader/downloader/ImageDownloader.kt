package com.dergruenkohl.newsillyimagedownloader.downloader

import com.dergruenkohl.newsillyimagedownloader.data.DatabaseService
import com.dergruenkohl.newsillyimagedownloader.data.Femboy
import com.dergruenkohl.newsillyimagedownloader.data.Rating
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.request.basicAuth
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.headers
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.copyTo
import java.io.File
import java.nio.channels.Channels
import kotlin.io.path.Path
import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds


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
    var recursions = 0

    private val rateLimitMutex = Mutex()
    private var nextRequestTime = 0L
    private val REQUEST_INTERVAL_MS = 300L // 200 requests/minute

    private suspend fun acquireRateLimit() {
        rateLimitMutex.withLock {
            val now = System.currentTimeMillis()

            if (now < nextRequestTime) {
                val delay = (nextRequestTime - now).milliseconds
                logger.info { "Rate limiting for $delay" }
                delay(delay)
            }

            nextRequestTime = maxOf(System.currentTimeMillis(), nextRequestTime) + REQUEST_INTERVAL_MS
        }
    }


    suspend fun downloadImages() {
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
            logger.info { "Finished downloading images with ${errors.get()} errors." }
            if (errors.get() > 0) {
                logger.error { "Images failed with ${errors.get()} errors, trying again in a minute" }
                errors.set(0)
                delay(1.minutes)
                downloadImages()
            }
            // Final progress update
            val progressText = """
            Processed: ${totalProcessed.get()} / $totalImages
            Downloaded: ${totalDownloaded.get()}
            Skipped: ${totalProcessed.get() - totalDownloaded.get() - errors.get()}
            FilesCreated: ${totalFilesCreated.get()}
            Progress: 100.00 %
            Errors: ${errors.get()}
        """.trimIndent()
            withContext(Dispatchers.Main) { onProgressUpdate(progressText) }
        }
    }


    suspend fun downloadImage(image: Femboy) {
        controller?.checkPausedOrStopped()

        val extension = extractExtension(image.fileUrl)
        val isZip = extension == "zip"

        // The canonical file in the base rating folder
        val file = buildCanonicalFile(image, extension)
        val gifFile = if (isZip) buildCanonicalFile(image, "gif") else null

        totalProcessed.incrementAndGet()

        // Tag-specific link targets (only relevant when split is enabled)
        val linkTargets = buildLinkTargets(image, extension)
        val gifLinkTargets = if (isZip) buildLinkTargets(image, "gif") else emptyList()

        if (file.exists()) {
            logger.info { "Image ${image.id} already exists, skipping" }

            if (isZip && gifFile != null && !gifFile.exists()) {
                try {
                    createGifFromZip(file, gifFile)
                    totalFilesCreated.incrementAndGet()
                } catch (e: Exception) {
                    logger.error(e) { "Failed to create GIF from existing ZIP for ${image.id}" }
                    errors.incrementAndGet()
                }
            }

            linkTargets.forEach { link ->
                createLink(link, file)
            }
            if (gifFile?.exists() == true) {
                gifLinkTargets.forEach { link ->
                    createLink(link, gifFile)
                }
            }
            return
        }

        controller?.checkPausedOrStopped()

        acquireRateLimit()

        val response = client.get(image.fileUrl){
            headers {
                basicAuth("DerGruenkohl", "hcF4NWqA34xTN2JG6TaNkhbC")
            }
        }
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

        if (isZip && gifFile != null) {
            try {
                createGifFromZip(file, gifFile)
                totalFilesCreated.incrementAndGet()
            } catch (e: Exception) {
                logger.error(e) { "Failed to create GIF from ZIP for ${image.id}" }
                errors.incrementAndGet()
            }
        }

        // Create hard links (or symlinks as fallback) in tag-specific folders
        linkTargets.forEach { link ->
            createLink(link, file)
        }
        if (gifFile?.exists() == true) {
            gifLinkTargets.forEach { link ->
                createLink(link, gifFile)
            }
        }
    }

    private fun extractExtension(fileUrl: String): String {
        val normalized = fileUrl.substringBefore('?').substringBefore('#')
        return normalized.substringAfterLast('.', "bin").lowercase()
    }

    private fun buildCanonicalFile(image: Femboy, extension: String): File =
        when (image.rating) {
            Rating.SFW -> basePath.resolve("sfw/${image.id}.$extension").toFile()
            Rating.NSFW -> basePath.resolve("nsfw/${image.id}.$extension").toFile()
            Rating.QUESTIONABLE ->
                basePath.resolve("questionable/${image.id}.$extension").toFile()
        }

    private fun buildLinkTargets(image: Femboy, extension: String): List<File> {
        if (!split) return emptyList()

        return image.names
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
    }

    private fun createGifFromZip(zipFile: File, gifFile: File) {
        val tempDir = Files.createTempDirectory("nsid-zip-frames-").toFile()
        try {
            val frames = extractZipFrames(zipFile, tempDir)
            if (frames.isEmpty()) {
                throw IllegalStateException("ZIP contains no readable image frames: ${zipFile.path}")
            }

            gifFile.parentFile.mkdirs()
            runFfmpegGifConversion(frames, gifFile)
            logger.info { "Created GIF ${gifFile.path} from ${zipFile.path}" }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun extractZipFrames(zipFile: File, tempDir: File): List<File> =
        ZipFile(zipFile).use { zip ->
            Collections.list(zip.entries())
                .asSequence()
                .filter { !it.isDirectory && isImageEntry(it.name) }
                .sortedBy { it.name.lowercase() }
                .mapIndexed { index, entry ->
                    val frameExtension = extractExtension(entry.name)
                    val frameFile = File(tempDir, "frame_${index.toString().padStart(6, '0')}.$frameExtension")
                    zip.getInputStream(entry).use { input ->
                        frameFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    frameFile
                }
                .toList()
        }

    private fun runFfmpegGifConversion(frames: List<File>, gifFile: File, frameDelaySeconds: Double = 0.06) {
        val concatFile = File(gifFile.parentFile, "${gifFile.nameWithoutExtension}_frames.txt")
        try {
            concatFile.bufferedWriter().use { writer ->
                frames.forEach { frame ->
                    writer.appendLine("file ${frame.absolutePath}")
                    writer.appendLine("duration $frameDelaySeconds")
                }
                // Concat demuxer uses the final file line to apply the previous duration.
                writer.appendLine("file ${frames.last().absolutePath}")
            }

            val process =
                ProcessBuilder(
                    "ffmpeg",
                    "-hide_banner",
                    "-loglevel",
                    "error",
                    "-y",
                    "-f",
                    "concat",
                    "-safe",
                    "0",
                    "-i",
                    concatFile.absolutePath,
                    "-vf",
                    "split[s0][s1];[s0]palettegen=reserve_transparent=on[p];[s1][p]paletteuse",
                    "-loop",
                    "0",
                    gifFile.absolutePath
                )
                    .redirectErrorStream(true)
                    .start()

            val ffmpegOutput = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                throw IllegalStateException(
                    "ffmpeg failed with exit code $exitCode while creating ${gifFile.path}: $ffmpegOutput"
                )
            }
        } finally {
            concatFile.delete()
        }
    }

    private fun isImageEntry(entryName: String): Boolean {
        val lower = entryName.lowercase()
        return lower.endsWith(".png") ||
            lower.endsWith(".jpg") ||
            lower.endsWith(".jpeg") ||
            lower.endsWith(".webp") ||
            lower.endsWith(".bmp") ||
            lower.endsWith(".gif")
    }


    private fun createLink(link: File, target: File) {
        try {
            link.parentFile.mkdirs()
            Files.createLink(link.toPath(), target.toPath())
            logger.info { "Hard-linked ${target.path} -> ${link.path}" }
            totalFilesCreated.incrementAndGet()
        } catch (_: Exception) {
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

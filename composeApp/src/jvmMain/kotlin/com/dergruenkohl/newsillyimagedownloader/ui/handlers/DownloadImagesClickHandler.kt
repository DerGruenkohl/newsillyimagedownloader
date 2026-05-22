package com.dergruenkohl.newsillyimagedownloader.ui.handlers

import androidx.compose.runtime.MutableState
import com.dergruenkohl.newsillyimagedownloader.data.DatabaseService
import com.dergruenkohl.newsillyimagedownloader.data.Providers
import com.dergruenkohl.newsillyimagedownloader.downloader.DownloadController
import com.dergruenkohl.newsillyimagedownloader.downloader.ImageDownloader
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

fun handleDownloadImagesClick(
    scope: CoroutineScope,
    selectedProvider: Providers,
    setStatusMessage: (String) -> Unit,
    setIsDownloadingImages: (Boolean) -> Unit,
    setDownloadProgress: (String) -> Unit,
    databaseService: DatabaseService,
    client: HttpClient,
    tags: String,
    whiteListedTagsInput: String,
    basePath: String,
    splitByName: Boolean,
    controllerState: MutableState<DownloadController?>,
    downloadJobState: MutableState<Job?>,
) {
    val controller = DownloadController()
    controllerState.value = controller

    val job = Job()
    downloadJobState.value = job
    val downloadScope = CoroutineScope(scope.coroutineContext + job)

    downloadScope.launch {
        setIsDownloadingImages(true)
        setStatusMessage("Downloading images...")

        try {
            File(basePath).mkdirs()
            File("$basePath/sfw").mkdirs()
            File("$basePath/nsfw").mkdirs()
            File("$basePath/questionable").mkdirs()

            val tagList = tags.split(",").map { it.trim() }
            val whiteListedTags = whiteListedTagsInput.split(",").map { it.trim() }
            val imageDownloader = ImageDownloader(
                database = databaseService,
                client = client,
                tags = tagList,
                basePath = basePath,
                controller = controller,
                concurrency = 32,
                split = splitByName,
                whiteListedTags = whiteListedTags,
                onProgressUpdate = setDownloadProgress
            )
            imageDownloader.downloadImages()
            setStatusMessage(
                "Downloaded ${imageDownloader.totalDownloaded.get()} images with ${imageDownloader.errors.get()} errors"
            )
        } catch (_: CancellationException) {
            setStatusMessage("Download stopped")
        } catch (e: Exception) {
            setStatusMessage("Error: ${e.message}")
        } finally {
            setIsDownloadingImages(false)
            controllerState.value = null
            downloadJobState.value = null
        }
    }
}


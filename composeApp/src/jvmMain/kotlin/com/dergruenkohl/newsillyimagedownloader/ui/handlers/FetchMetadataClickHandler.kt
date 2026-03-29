package com.dergruenkohl.newsillyimagedownloader.ui.handlers

import com.dergruenkohl.newsillyimagedownloader.data.DatabaseService
import com.dergruenkohl.newsillyimagedownloader.data.Providers
import com.dergruenkohl.newsillyimagedownloader.downloader.DanboruDownloader
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

fun handleFetchMetadataClick(
    scope: CoroutineScope,
    selectedProvider: Providers,
    setIsDownloadingMetadata: (Boolean) -> Unit,
    setStatusMessage: (String) -> Unit,
    client: HttpClient,
    databaseService: DatabaseService,
    tags: String,
    maxPage: String,
) {
    scope.launch {
        if (selectedProvider != Providers.DANBOORU) {
            setStatusMessage("$selectedProvider is not implemented yet")
            return@launch
        }

        setIsDownloadingMetadata(true)
        setStatusMessage("Fetching metadata...")
        try {
            val downloader = DanboruDownloader(
                client = client,
                databaseService = databaseService,
                tags = tags.split(",").joinToString("+"),
                maxPage = maxPage.toIntOrNull() ?: 10
            )
            downloader.getFemboyMetadata()
            setStatusMessage("Metadata fetched successfully")
        } catch (e: Exception) {
            setStatusMessage("Error: ${e.message}")
        } finally {
            setIsDownloadingMetadata(false)
        }
    }
}


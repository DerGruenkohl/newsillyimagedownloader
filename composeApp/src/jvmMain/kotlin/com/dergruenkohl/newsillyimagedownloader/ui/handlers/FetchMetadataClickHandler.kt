package com.dergruenkohl.newsillyimagedownloader.ui.handlers

import com.dergruenkohl.newsillyimagedownloader.data.DatabaseService
import com.dergruenkohl.newsillyimagedownloader.data.Providers
import com.dergruenkohl.newsillyimagedownloader.downloader.DanboruDownloader
import com.dergruenkohl.newsillyimagedownloader.downloader.R34Downloader
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
        setIsDownloadingMetadata(true)
        setStatusMessage("Fetching metadata...")
        try {
            val downloader = when (selectedProvider){
                Providers.DANBOORU -> {
                    DanboruDownloader(
                        client = client,
                        databaseService = databaseService,
                        tags = tags.split(",").joinToString("+"),
                        maxPage = maxPage.toIntOrNull() ?: 10
                    )
                }
                Providers.R34 -> {
                    R34Downloader(
                        client = client,
                        databaseService = databaseService,
                        tags = tags.split(",").joinToString("+"),
                        maxPage = maxPage.toIntOrNull() ?: 10
                    )

                }
            }
            setStatusMessage("Fetching metadata using ${selectedProvider.name.lowercase()}")
            downloader.fetchMetadata()
            setStatusMessage("Metadata fetched successfully")
        } catch (e: Exception) {
            setStatusMessage("Error: ${e.message}")
        } finally {
            setIsDownloadingMetadata(false)
        }
    }
}


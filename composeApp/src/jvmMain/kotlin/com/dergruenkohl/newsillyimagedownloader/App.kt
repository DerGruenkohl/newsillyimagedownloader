package com.dergruenkohl.newsillyimagedownloader

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dergruenkohl.newsillyimagedownloader.data.DatabaseService
import com.dergruenkohl.newsillyimagedownloader.downloader.DanboruDownloader
import com.dergruenkohl.newsillyimagedownloader.downloader.ImageDownloader
import io.ktor.client.*
import io.ktor.client.engine.apache5.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File

@Composable
fun App() {
    val scope = rememberCoroutineScope()
    val databaseService = remember { DatabaseService() }
    val client = remember {
        HttpClient(Apache5) {
            install(ContentNegotiation) {
                json(Json {
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
            install(Logging) {
                level = LogLevel.INFO
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 60000
            }
            followRedirects = true
        }
    }

    var tags by remember { mutableStateOf("astolfo_(fate)") }
    var basePath by remember { mutableStateOf("./downloads") }
    var isDownloadingMetadata by remember { mutableStateOf(false) }
    var isDownloadingImages by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Ready") }
    var downloadProgress by remember { mutableStateOf("") }
    var maxPage by remember { mutableStateOf("10") }

    MaterialTheme(
        colorScheme = darkColorScheme()
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Image Downloader",
                    style = MaterialTheme.typography.headlineMedium
                )

                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("Tags (comma separated)") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isDownloadingMetadata && !isDownloadingImages
                )

                OutlinedTextField(
                    value = basePath,
                    onValueChange = { basePath = it },
                    label = { Text("Download Path") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isDownloadingMetadata && !isDownloadingImages
                )
                OutlinedTextField(
                    value = maxPage,
                    onValueChange = { maxPage = it },
                    label = { Text("Max Pages") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isDownloadingMetadata && !isDownloadingImages
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                isDownloadingMetadata = true
                                statusMessage = "Fetching metadata..."
                                try {
                                    val downloader = DanboruDownloader(
                                        client = client,
                                        databaseService = databaseService,
                                        tags = tags.split(",").joinToString("+"),
                                        maxPage = maxPage.toIntOrNull() ?: 10
                                    )

                                    downloader.getFemboyMetadata()
                                    statusMessage = "Metadata fetched successfully"
                                } catch (e: Exception) {
                                    statusMessage = "Error: ${e.message}"
                                } finally {
                                    isDownloadingMetadata = false
                                }
                            }
                        },
                        enabled = !isDownloadingMetadata && !isDownloadingImages,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Fetch Metadata")
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                isDownloadingImages = true
                                statusMessage = "Downloading images..."
                                try {
                                    File(basePath).mkdirs()
                                    File("$basePath/sfw").mkdirs()
                                    File("$basePath/nsfw").mkdirs()
                                    File("$basePath/questionable").mkdirs()

                                    val tagList = tags.split(",").map { it.trim() }
                                    val imageDownloader = ImageDownloader(
                                        database = databaseService,
                                        client = client,
                                        tags = tagList,
                                        basePath = basePath
                                    )
                                    imageDownloader.downloadImages()
                                    statusMessage = "Downloaded ${imageDownloader.totalDownloaded} images with ${imageDownloader.errors} errors"
                                } catch (e: Exception) {
                                    statusMessage = "Error: ${e.message}"
                                } finally {
                                    isDownloadingImages = false
                                }
                            }
                        },
                        enabled = !isDownloadingMetadata && !isDownloadingImages,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Download Images")
                    }
                }

                if (isDownloadingMetadata || isDownloadingImages) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Status",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (downloadProgress.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = downloadProgress,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

package com.dergruenkohl.newsillyimagedownloader

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dergruenkohl.newsillyimagedownloader.data.DatabaseService
import com.dergruenkohl.newsillyimagedownloader.data.Providers
import com.dergruenkohl.newsillyimagedownloader.downloader.DownloadController
import com.dergruenkohl.newsillyimagedownloader.ui.*
import com.dergruenkohl.newsillyimagedownloader.ui.handlers.handleDownloadImagesClick
import com.dergruenkohl.newsillyimagedownloader.ui.handlers.handleFetchMetadataClick
import io.ktor.client.*
import io.ktor.client.engine.apache5.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Job
import kotlinx.serialization.json.Json

@Composable
fun App() {
    val scope = rememberCoroutineScope()
    val databaseService = remember { DatabaseService() }
    val client = remember {
        HttpClient(Apache5) {
            install(UserAgent) {
                agent = "Silly goober"
            }
            install(ContentNegotiation) {
                json(Json {
                    isLenient = true
                    ignoreUnknownKeys = true
                })
            }
            install(Logging) {
                level = LogLevel.HEADERS
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 60000
            }
            expectSuccess = true
            install(HttpRequestRetry) {
                retryOnServerErrors(maxRetries = 5)
                retryOnException(maxRetries = 5)
                exponentialDelay()
            }
            followRedirects = true
        }
    }

    var tags by remember { mutableStateOf("umamusume") }
    var basePath by remember { mutableStateOf("./downloads") }
    var isDownloadingMetadata by remember { mutableStateOf(false) }
    var isDownloadingImages by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Ready") }
    var downloadProgress by remember { mutableStateOf("") }
    var maxPage by remember { mutableStateOf("10") }
    var splitByName by remember { mutableStateOf(true) }
    var whiteListedTags by remember { mutableStateOf("umamusume,hayakawa_tazuna,anshinzawa_sasami,kashimoto_riko,kiryuin_aoi") }
    var selectedProvider by remember { mutableStateOf(Providers.DANBOORU) }

    // controller and job refs
    val controllerState = remember { mutableStateOf<DownloadController?>(null) }
    val downloadJobState = remember { mutableStateOf<Job?>(null) }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(0xFF3B81FF),
        )
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

                TagInputRow(
                    tags = tags,
                    onTagsChange = { tags = it },
                    whiteListedTags = whiteListedTags,
                    onWhiteListedTagsChange = { whiteListedTags = it },
                    enabled = !isDownloadingMetadata && !isDownloadingImages
                )
                DownloadOptions(
                    basePath = basePath,
                    basePathChange = { basePath = it },
                    enabled = !isDownloadingMetadata && !isDownloadingImages,
                    maxPage = maxPage,
                    maxPageChange = { maxPage = it }
                )
                ProviderChange(
                    selectedProvider = selectedProvider,
                    onProviderChange = { selectedProvider = it },
                    enabled = !isDownloadingMetadata && !isDownloadingImages
                )
                SplitByNameSwitch(
                    checked = splitByName,
                    onCheckedChange = { splitByName = it },
                    enabled = !isDownloadingMetadata && !isDownloadingImages
                )


                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FetchMetadataButton(
                        onClick = {
                            handleFetchMetadataClick(
                                scope = scope,
                                selectedProvider = selectedProvider,
                                setIsDownloadingMetadata = { isDownloadingMetadata = it },
                                setStatusMessage = { statusMessage = it },
                                client = client,
                                databaseService = databaseService,
                                tags = tags,
                                maxPage = maxPage
                            )
                        },
                        enabled = !isDownloadingMetadata && !isDownloadingImages,
                        modifier = Modifier.weight(1f)
                    )

                    DownloadImagesButton(
                        onClick = {
                            handleDownloadImagesClick(
                                scope = scope,
                                selectedProvider = selectedProvider,
                                setStatusMessage = { statusMessage = it },
                                setIsDownloadingImages = { isDownloadingImages = it },
                                setDownloadProgress = { downloadProgress = it },
                                databaseService = databaseService,
                                client = client,
                                tags = tags,
                                whiteListedTagsInput = whiteListedTags,
                                basePath = basePath,
                                splitByName = splitByName,
                                controllerState = controllerState,
                                downloadJobState = downloadJobState
                            )
                        },
                        enabled = !isDownloadingMetadata && !isDownloadingImages,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Pause / Stop controls
                if (isDownloadingImages) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val controller = controllerState.value
                        val isPaused = controller?.isPaused == true
                        Button(
                            onClick = {
                                controller?.let {
                                    if (it.isPaused) it.resume() else it.pause()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (isPaused) "Resume" else "Pause")
                        }
                        Button(
                            onClick = {
                                controllerState.value?.stop()
                                downloadJobState.value?.cancel()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Stop")
                        }
                    }
                }


                if (isDownloadingMetadata || isDownloadingImages) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
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

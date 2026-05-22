package com.dergruenkohl.newsillyimagedownloader.downloader

import com.dergruenkohl.newsillyimagedownloader.data.DatabaseService
import io.ktor.client.HttpClient

interface MetadataDownloader {
    val client: HttpClient
    val databaseService: DatabaseService
    val tags: String
    val maxPage: Int
    suspend fun fetchMetadata()
}
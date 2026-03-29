package com.dergruenkohl.newsillyimagedownloader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DownloadOptions(
    basePath: String,
    basePathChange: (String) -> Unit,
    enabled: Boolean,
    maxPage: String,
    maxPageChange: (String) -> Unit,
) = Column {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = basePath,
            onValueChange = basePathChange,
            label = { Text("Download Path") },
            modifier = Modifier.weight(1f),
            enabled = enabled
        )
        OutlinedTextField(
            value = maxPage,
            onValueChange = maxPageChange,
            label = { Text("Max Pages") },
            modifier = Modifier.weight(1f),
            enabled = enabled
        )
    }
}
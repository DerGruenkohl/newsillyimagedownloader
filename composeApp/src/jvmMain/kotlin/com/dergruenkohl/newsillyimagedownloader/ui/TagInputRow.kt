package com.dergruenkohl.newsillyimagedownloader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TagInputRow(
    tags: String,
    onTagsChange: (String) -> Unit,
    whiteListedTags: String,
    onWhiteListedTagsChange: (String) -> Unit,
    enabled: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedTextField(
            value = tags,
            onValueChange = onTagsChange,
            label = { Text("Tags (comma separated)") },
            modifier = Modifier.weight(1f),
            enabled = enabled
        )

        OutlinedTextField(
            value = whiteListedTags,
            onValueChange = onWhiteListedTagsChange,
            label = { Text("Whitelisted tags") },
            modifier = Modifier.weight(1f),
            enabled = enabled
        )
    }
}

package com.dergruenkohl.newsillyimagedownloader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dergruenkohl.newsillyimagedownloader.data.Providers

@Composable
fun ProviderChange(
	selectedProvider: Providers,
	onProviderChange: (Providers) -> Unit,
	enabled: Boolean,
	modifier: Modifier = Modifier,
) {
	var expanded by remember { mutableStateOf(false) }

	Card(modifier = modifier.fillMaxWidth()) {
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(16.dp),
			horizontalArrangement = Arrangement.spacedBy(12.dp)
		) {
			Column(modifier = Modifier.weight(1f)) {
				Text(
					text = "Provider",
					style = MaterialTheme.typography.bodyLarge
				)
				Text(
					text = "Select where metadata and images are sourced from.",
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant
				)
			}

			Box {
				OutlinedButton(
					onClick = { expanded = true },
					enabled = enabled
				) {
					Text(selectedProvider.name)
				}

				DropdownMenu(
					expanded = expanded,
					onDismissRequest = { expanded = false }
				) {
					Providers.entries.forEach { provider ->
						DropdownMenuItem(
							text = { Text(provider.name) },
							onClick = {
								onProviderChange(provider)
								expanded = false
							}
						)
					}
				}
			}
		}
	}
}

package com.example.timelineviewer.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TimelineImportDialog(
    onDismiss: () -> Unit,
    onImportJson: suspend (String, String) -> Boolean,
    onAddCustomJourney: suspend (String, String, Double, Double, Double, Double, List<String>) -> Long,
    modifier: Modifier = Modifier
) {
    var titleInput by remember { mutableStateOf("New Google Takeout Journey") }
    var jsonInput by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var statusText by remember { mutableStateOf("Ready to import") }

    val coroutineScope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = { if (!isImporting) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("Import Timeline Data")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Upload Google Location History, Takeout ZIP/JSON, or paste timeline coordinates below.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    label = { Text("Journey Title") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("import_title_field")
                )

                OutlinedTextField(
                    value = jsonInput,
                    onValueChange = { jsonInput = it },
                    label = { Text("Timeline JSON / GeoJSON Data") },
                    placeholder = { Text("Paste JSON array of [{lat, lng}, ...] or Google Takeout structure") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("import_json_field")
                )

                if (isImporting) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Divider()

                Text(
                    text = "Or create a quick test journey:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isImporting = true
                                statusText = "Generating Paris City Tour..."
                                progress = 0.3f
                                delay(300)
                                progress = 0.7f
                                delay(300)
                                onAddCustomJourney(
                                    "Paris Sightseeing Walk",
                                    "Eiffel Tower to Louvre Museum & Notre Dame",
                                    48.8584, 2.2945,
                                    48.8606, 2.3376,
                                    listOf("Eiffel Tower", "Tuileries Garden", "Louvre Museum")
                                )
                                progress = 1.0f
                                delay(200)
                                isImporting = false
                                onDismiss()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("import_preset_paris"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Paris Walk", style = MaterialTheme.typography.labelSmall)
                    }

                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isImporting = true
                                statusText = "Generating NYC Expressway..."
                                progress = 0.3f
                                delay(300)
                                progress = 0.7f
                                delay(300)
                                onAddCustomJourney(
                                    "New York Manhattan Express",
                                    "Central Park south to Wall Street & Brooklyn Bridge",
                                    40.7829, -73.9654,
                                    40.7061, -73.9969,
                                    listOf("Times Square", "Empire State", "Wall Street")
                                )
                                progress = 1.0f
                                delay(200)
                                isImporting = false
                                onDismiss()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("import_preset_nyc"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("NYC Drive", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (jsonInput.isBlank()) {
                        // Create default dummy if blank
                        coroutineScope.launch {
                            isImporting = true
                            statusText = "Processing timeline dataset..."
                            progress = 0.5f
                            delay(400)
                            onAddCustomJourney(
                                titleInput,
                                "Imported location history track",
                                51.5074, -0.1278,
                                51.5007, -0.1246,
                                listOf("Big Ben", "London Eye")
                            )
                            isImporting = false
                            onDismiss()
                        }
                    } else {
                        coroutineScope.launch {
                            isImporting = true
                            statusText = "Parsing timeline JSON..."
                            progress = 0.2f
                            delay(300)
                            statusText = "Detecting stops & transport modes..."
                            progress = 0.6f
                            delay(300)
                            val success = onImportJson(jsonInput, titleInput)
                            progress = 1.0f
                            delay(200)
                            isImporting = false
                            if (success) {
                                onDismiss()
                            } else {
                                statusText = "Failed to parse JSON string"
                            }
                        }
                    }
                },
                enabled = !isImporting,
                modifier = Modifier.testTag("confirm_import_button")
            ) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isImporting
            ) {
                Text("Cancel")
            }
        }
    )
}

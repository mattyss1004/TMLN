package com.example.timelineviewer.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
    onImportDocument: suspend (Uri, String) -> Boolean,
    onAddCustomJourney: suspend (String, String, Double, Double, Double, Double, List<String>) -> Long,
    modifier: Modifier = Modifier
) {
    var titleInput by remember { mutableStateOf("New Google Takeout Journey") }
    var jsonInput by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var statusText by remember { mutableStateOf("Ready to import") }
    val coroutineScope = rememberCoroutineScope()

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                isImporting = true
                progress = 0.1f
                statusText = "Reading timeline document…"
                val success = onImportDocument(uri, titleInput)
                progress = 1f
                isImporting = false
                if (success) onDismiss() else statusText = "The document could not be parsed as a supported timeline file"
            }
        }
    }

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
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Choose a Google Timeline JSON or GeoJSON file for a memory-safe import, or paste a short route below.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = titleInput,
                    onValueChange = { titleInput = it },
                    label = { Text("Journey title") },
                    singleLine = true,
                    enabled = !isImporting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("import_title_field")
                )

                OutlinedButton(
                    onClick = {
                        documentPicker.launch(
                            arrayOf("application/json", "application/geo+json", "text/plain", "text/*")
                        )
                    },
                    enabled = !isImporting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("choose_import_document")
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Choose JSON or GeoJSON file")
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    HorizontalDivider(modifier = Modifier.weight(1f))
                    Text(
                        text = "  OR PASTE A SMALL ROUTE  ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f))
                }

                OutlinedTextField(
                    value = jsonInput,
                    onValueChange = { jsonInput = it },
                    label = { Text("Timeline JSON / GeoJSON") },
                    placeholder = { Text("[{\"lat\": 50.08, \"lng\": 14.42}, …]") },
                    minLines = 3,
                    maxLines = 5,
                    enabled = !isImporting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("import_json_field")
                )

                if (isImporting || statusText.startsWith("The document") || statusText.startsWith("Failed")) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isImporting) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (statusText.startsWith("The document") || statusText.startsWith("Failed")) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                }

                HorizontalDivider()

                Text(
                    text = "Or create a quick test journey",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PresetJourneyButton(
                        label = "Paris Walk",
                        testTag = "import_preset_paris",
                        enabled = !isImporting,
                        onStarted = {
                            isImporting = true
                            progress = 0.4f
                            statusText = "Creating Paris journey…"
                        },
                        onCompleted = {
                            isImporting = false
                            onDismiss()
                        }
                    ) {
                        onAddCustomJourney(
                            "Paris Sightseeing Walk",
                            "Eiffel Tower to Louvre Museum & Notre Dame",
                            48.8584, 2.2945,
                            48.8606, 2.3376,
                            listOf("Eiffel Tower", "Tuileries Garden", "Louvre Museum")
                        )
                    }
                    PresetJourneyButton(
                        label = "NYC Drive",
                        testTag = "import_preset_nyc",
                        enabled = !isImporting,
                        onStarted = {
                            isImporting = true
                            progress = 0.4f
                            statusText = "Creating New York journey…"
                        },
                        onCompleted = {
                            isImporting = false
                            onDismiss()
                        }
                    ) {
                        onAddCustomJourney(
                            "New York Manhattan Express",
                            "Central Park south to Wall Street & Brooklyn Bridge",
                            40.7829, -73.9654,
                            40.7061, -73.9969,
                            listOf("Times Square", "Empire State", "Wall Street")
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    coroutineScope.launch {
                        isImporting = true
                        if (jsonInput.isBlank()) {
                            statusText = "Generating a small test journey…"
                            progress = 0.4f
                            delay(180)
                            onAddCustomJourney(
                                titleInput,
                                "Imported location history track",
                                51.5074, -0.1278,
                                51.5007, -0.1246,
                                listOf("Big Ben", "London Eye")
                            )
                            progress = 1f
                            isImporting = false
                            onDismiss()
                        } else {
                            statusText = "Parsing and cleaning timeline data…"
                            progress = 0.3f
                            val success = onImportJson(jsonInput, titleInput)
                            progress = 1f
                            isImporting = false
                            if (success) onDismiss() else statusText = "Failed to parse this JSON string"
                        }
                    }
                },
                enabled = !isImporting,
                modifier = Modifier.testTag("confirm_import_button")
            ) {
                Text(if (jsonInput.isBlank()) "Create test route" else "Import pasted route")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isImporting) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun PresetJourneyButton(
    label: String,
    testTag: String,
    enabled: Boolean,
    onStarted: () -> Unit,
    onCompleted: () -> Unit,
    onCreate: suspend () -> Long
) {
    val coroutineScope = rememberCoroutineScope()
    Button(
        onClick = {
            coroutineScope.launch {
                onStarted()
                onCreate()
                onCompleted()
            }
        },
        enabled = enabled,
        modifier = Modifier
            .weight(1f)
            .testTag(testTag),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

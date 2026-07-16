package com.bobai.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bobai.studio.editor.EditorViewModel
import com.bobai.studio.editor.ProcessingStage

@Composable
fun EditorScreen(projectId: String, viewModel: EditorViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    var showWatermarkSelector by remember { mutableStateOf(false) }

    LaunchedEffect(projectId) { viewModel.loadProject(projectId) }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
        Column(Modifier.fillMaxSize()) {
            TopBar(projectName = state.project?.name ?: "Bob AI Studio")

            // Preview area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black)
            ) {
                if (showWatermarkSelector) {
                    WatermarkRegionSelector(
                        onConfirm = { region ->
                            viewModel.removeWatermark(region, videoWidth = 1080, videoHeight = 1920)
                            showWatermarkSelector = false
                        },
                        onCancel = { showWatermarkSelector = false }
                    )
                } else {
                    Text(
                        "Предпросмотр видео",
                        color = Color(0xFF666666),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                if (state.processingStage == ProcessingStage.REMOVING_WATERMARK ||
                    state.processingStage == ProcessingStage.ANALYZING_SCENES ||
                    state.processingStage == ProcessingStage.REPLACING_FRAGMENT
                ) {
                    ProcessingOverlay(state.processingStage)
                }
            }

            AudioTrackView(
                waveform = state.waveform,
                beats = state.beats,
                totalDurationMs = state.project?.durationMs ?: 0L,
                beatPlacementMode = state.beatPlacementModeActive,
                onAddBeat = viewModel::addBeatAt,
                onRemoveBeat = viewModel::removeBeat,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            TimelineView(
                fragments = state.fragments,
                pixelsPerMs = 0.08f,
                onDragFragmentEdge = { fragment, rawEnd ->
                    val snapped = viewModel.snapDragTime(rawEnd)
                    // ViewModel currently exposes snap logic; wiring a full
                    // trim-commit call is the natural next hook point here.
                },
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            BottomToolbar(
                beatModeActive = state.beatPlacementModeActive,
                onRemoveWatermark = { showWatermarkSelector = true },
                onChangeEdit = { viewModel.startChangeEdit { null } },
                onToggleBeatMode = viewModel::toggleBeatPlacementMode
            )
        }

        if (state.showChangeEditGrid) {
            ChangeEditGrid(
                fragments = state.fragments,
                onFragmentTap = { /* opens gallery picker in a full integration */ },
                onStretchImageDuration = { _, _ -> },
                onClose = viewModel::dismissChangeEditGrid
            )
        }

        state.errorMessage?.let { message ->
            AlertDialog(
                onDismissRequest = viewModel::clearError,
                confirmButton = { TextButton(onClick = viewModel::clearError) { Text("Ок") } },
                title = { Text("Ошибка") },
                text = { Text(message) }
            )
        }
    }
}

@Composable
private fun TopBar(projectName: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(projectName, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Button(
            onClick = { /* export */ },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF37E5E5))
        ) { Text("Экспорт", color = Color.Black, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun ProcessingOverlay(stage: ProcessingStage) {
    val label = when (stage) {
        ProcessingStage.REMOVING_WATERMARK -> "Удаляем водяной знак…"
        ProcessingStage.ANALYZING_SCENES -> "ИИ анализирует кадры…"
        ProcessingStage.REPLACING_FRAGMENT -> "Заменяем фрагмент…"
        else -> ""
    }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color(0xFFFF2E2E))
            Spacer(Modifier.height(12.dp))
            Text(label, color = Color.White)
        }
    }
}

@Composable
private fun BottomToolbar(
    beatModeActive: Boolean,
    onRemoveWatermark: () -> Unit,
    onChangeEdit: () -> Unit,
    onToggleBeatMode: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF141414))
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ToolbarButton(Icons.Default.Delete, "Remove\nWatermark", onRemoveWatermark)
        ToolbarButton(Icons.Default.AutoAwesome, "Change\nEdit", onChangeEdit)
        ToolbarButton(
            Icons.Default.MusicNote,
            if (beatModeActive) "Готово" else "Поставить\nритм",
            onToggleBeatMode
        )
    }
}

@Composable
private fun ToolbarButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 8.dp)
    ) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = label, tint = Color.White)
        }
        Text(label, color = Color(0xFFA0A0A5), style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

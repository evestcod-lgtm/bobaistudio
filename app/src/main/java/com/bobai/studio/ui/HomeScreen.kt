package com.bobai.studio.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bobai.studio.editor.EditorViewModel

@Composable
fun HomeScreen(onProjectOpened: (String) -> Unit, viewModel: EditorViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()

    val pickVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            // Duration probing (MediaMetadataRetriever) happens at the call
            // site in a full build; wired here with a placeholder duration
            // so the import → slicing pipeline is exercised end-to-end.
            viewModel.importVideo(it.toString(), durationMs = 17_000)
        }
    }

    LaunchedEffect(state.project) {
        state.project?.let { onProjectOpened(it.id) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Bob AI Studio",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Загрузи эдит из TikTok — убери водяной знак\nили пересобери фрагменты с ИИ",
                color = Color(0xFFA0A0A5),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = { pickVideoLauncher.launch("video/*") },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF2E2E)),
                modifier = Modifier.height(52.dp)
            ) {
                Icon(Icons.Default.VideoLibrary, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Загрузить видео", fontWeight = FontWeight.Bold)
            }
        }
    }
}

package com.bobai.studio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bobai.studio.data.FragmentSourceType
import com.bobai.studio.data.TimelineFragment
import com.bobai.studio.ui.theme.BobRed
import com.bobai.studio.ui.theme.BobSurface

/**
 * The "Change Edit" screen: every timeline fragment is shown as a tile
 * (mirroring the frame-strip look of the reference screenshot). Tapping a
 * tile opens a picker to swap that fragment's media; for image fragments a
 * horizontal drag on the duration chip stretches/shrinks how long that
 * still frame holds on screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangeEditGrid(
    fragments: List<TimelineFragment>,
    onFragmentTap: (TimelineFragment) -> Unit,
    onStretchImageDuration: (TimelineFragment, deltaMs: Long) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
    ) {
        TopAppBar(
            title = { Text("Change Edit", fontWeight = FontWeight.Bold) },
            navigationIcon = {
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Закрыть") }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A0A0A), titleContentColor = Color.White)
        )

        Text(
            "Нажмите на фрагмент, чтобы заменить его своим фото или видео",
            color = Color(0xFFA0A0A5),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(fragments, key = { it.id }) { fragment ->
                FragmentTile(
                    fragment = fragment,
                    onTap = { onFragmentTap(fragment) },
                    onStretch = { delta -> onStretchImageDuration(fragment, delta) }
                )
            }
        }
    }
}

@Composable
private fun FragmentTile(
    fragment: TimelineFragment,
    onTap: () -> Unit,
    onStretch: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(BobSurface)
            .pointerInput(fragment.id) {
                detectTapGestures(onTap = { onTap() })
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
        ) {
            if (fragment.thumbnailPath != null) {
                AsyncImage(
                    model = fragment.thumbnailPath,
                    contentDescription = fragment.aiSceneLabel,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    Modifier.fillMaxSize().background(Color(0xFF2A2A2D)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Image, contentDescription = null, tint = Color(0xFF666666)) }
            }

            // Edit overlay hint
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(BobRed.copy(alpha = 0.9f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Заменить", tint = Color.White, modifier = Modifier.size(14.dp))
            }

            if (fragment.sourceType == FragmentSourceType.IMAGE) {
                // Duration stretch handle — horizontal drag lengthens/shortens hold time
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .pointerInput(fragment.id) {
                            detectHorizontalDragGestures { change, dragAmount ->
                                change.consume()
                                // ~10ms of duration per pixel dragged
                                onStretch((dragAmount * 10).toLong())
                            }
                        }
                ) {
                    Text("${fragment.durationMs / 1000f}s", color = Color.White, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Text(
            text = fragment.aiSceneLabel ?: "Фрагмент ${fragment.orderIndex + 1}",
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            modifier = Modifier.padding(6.dp)
        )
    }
}

package com.bobai.studio.editor

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bobai.studio.ai.SceneAnalyzer
import com.bobai.studio.audio.BeatEngine
import com.bobai.studio.audio.WaveformPoint
import com.bobai.studio.data.AppDatabase
import com.bobai.studio.data.BeatMarker
import com.bobai.studio.data.FragmentSourceType
import com.bobai.studio.data.Project
import com.bobai.studio.data.TimelineFragment
import com.bobai.studio.data.WatermarkRegion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

enum class ProcessingStage { IDLE, REMOVING_WATERMARK, ANALYZING_SCENES, REPLACING_FRAGMENT, DONE, ERROR }

data class EditorUiState(
    val project: Project? = null,
    val fragments: List<TimelineFragment> = emptyList(),
    val waveform: List<WaveformPoint> = emptyList(),
    val beats: List<BeatMarker> = emptyList(),
    val processingStage: ProcessingStage = ProcessingStage.IDLE,
    val errorMessage: String? = null,
    val showChangeEditGrid: Boolean = false,
    val beatPlacementModeActive: Boolean = false
)

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.get(application)
    private val watermarkRemover = WatermarkRemover()
    private val fragmentReplacer = FragmentReplacer()
    private val sceneAnalyzer = SceneAnalyzer()
    private val beatEngine = BeatEngine()

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private val workingDir: String
        get() = getApplication<Application>().cacheDir.absolutePath

    fun loadProject(projectId: String) {
        viewModelScope.launch {
            val project = db.projectDao().getProject(projectId) ?: return@launch
            _uiState.value = _uiState.value.copy(project = project, beats = project.beatMarkers)
            db.projectDao().observeFragments(projectId)
        }
    }

    /** Import a fresh TikTok edit: slice into fragments and persist a new project. */
    fun importVideo(sourcePath: String, durationMs: Long, sliceEveryMs: Long = 1500) {
        viewModelScope.launch {
            val projectId = UUID.randomUUID().toString()
            val fragments = mutableListOf<TimelineFragment>()
            var t = 0L
            var order = 0
            while (t < durationMs) {
                val end = (t + sliceEveryMs).coerceAtMost(durationMs)
                fragments.add(
                    TimelineFragment(
                        id = UUID.randomUUID().toString(),
                        projectId = projectId,
                        orderIndex = order,
                        sourceType = FragmentSourceType.VIDEO,
                        sourcePath = sourcePath,
                        trimStartMs = t,
                        trimEndMs = end
                    )
                )
                t = end
                order++
            }
            val project = Project(
                id = projectId,
                name = "Проект ${order}",
                originalVideoPath = sourcePath,
                createdAtMs = System.currentTimeMillis(),
                durationMs = durationMs
            )
            db.projectDao().upsertProject(project)
            db.projectDao().upsertFragments(fragments)
            _uiState.value = _uiState.value.copy(project = project, fragments = fragments)
        }
    }

    // ---------------- Remove Watermark ----------------

    fun removeWatermark(region: WatermarkRegion, videoWidth: Int, videoHeight: Int) {
        val project = _uiState.value.project ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(processingStage = ProcessingStage.REMOVING_WATERMARK)
            val outputPath = "$workingDir/${project.id}_clean.mp4"
            when (val result = watermarkRemover.remove(project.originalVideoPath, outputPath, region, videoWidth, videoHeight)) {
                is WatermarkResult.Success -> {
                    val updated = project.copy(
                        originalVideoPath = result.outputPath,
                        watermarkRegion = region,
                        watermarkRemoved = true
                    )
                    db.projectDao().upsertProject(updated)
                    _uiState.value = _uiState.value.copy(
                        project = updated,
                        processingStage = ProcessingStage.DONE
                    )
                }
                is WatermarkResult.Failure -> {
                    _uiState.value = _uiState.value.copy(
                        processingStage = ProcessingStage.ERROR,
                        errorMessage = "Не удалось убрать водяной знак: ${result.message.take(200)}"
                    )
                }
            }
        }
    }

    // ---------------- Change Edit ----------------

    /** Kick off AI scene analysis for every fragment, then reveal the swap grid. */
    fun startChangeEdit(keyFrameProvider: suspend (TimelineFragment) -> Bitmap?) {
        val fragments = _uiState.value.fragments
        if (fragments.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(processingStage = ProcessingStage.ANALYZING_SCENES)
            val analyzed = fragments.map { fragment ->
                val frame = keyFrameProvider(fragment)
                if (frame == null) {
                    fragment
                } else {
                    try {
                        val analysis = sceneAnalyzer.analyzeFrame(frame)
                        fragment.copy(aiSceneLabel = analysis.label, aiSceneTags = analysis.tags)
                    } catch (e: Exception) {
                        fragment.copy(aiSceneLabel = "Фрагмент ${fragment.orderIndex + 1}")
                    }
                }
            }
            db.projectDao().upsertFragments(analyzed)
            _uiState.value = _uiState.value.copy(
                fragments = analyzed,
                processingStage = ProcessingStage.DONE,
                showChangeEditGrid = true
            )
        }
    }

    fun replaceFragmentWithVideo(fragment: TimelineFragment, newVideoPath: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(processingStage = ProcessingStage.REPLACING_FRAGMENT)
            when (val result = fragmentReplacer.replaceWithVideo(fragment, newVideoPath, workingDir)) {
                is ReplaceResult.Success -> applyReplacedFragment(result.fragment)
                is ReplaceResult.Failure -> _uiState.value = _uiState.value.copy(
                    processingStage = ProcessingStage.ERROR,
                    errorMessage = result.message.take(200)
                )
            }
        }
    }

    fun replaceFragmentWithImage(fragment: TimelineFragment, newImagePath: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(processingStage = ProcessingStage.REPLACING_FRAGMENT)
            when (val result = fragmentReplacer.replaceWithImage(fragment, newImagePath, workingDir)) {
                is ReplaceResult.Success -> applyReplacedFragment(result.fragment)
                is ReplaceResult.Failure -> _uiState.value = _uiState.value.copy(
                    processingStage = ProcessingStage.ERROR,
                    errorMessage = result.message.take(200)
                )
            }
        }
    }

    private suspend fun applyReplacedFragment(updated: TimelineFragment) {
        db.projectDao().upsertFragment(updated)
        val newList = _uiState.value.fragments.map { if (it.id == updated.id) updated else it }
        _uiState.value = _uiState.value.copy(fragments = newList, processingStage = ProcessingStage.DONE)
    }

    // ---------------- Beats / magnet ----------------

    fun toggleBeatPlacementMode() {
        _uiState.value = _uiState.value.copy(beatPlacementModeActive = !_uiState.value.beatPlacementModeActive)
    }

    fun addBeatAt(timeMs: Long) {
        val project = _uiState.value.project ?: return
        val newBeat = BeatMarker(id = UUID.randomUUID().toString(), timeMs = timeMs, isAutoDetected = false)
        val updatedBeats = (_uiState.value.beats + newBeat).sortedBy { it.timeMs }
        persistBeats(project, updatedBeats)
    }

    fun removeBeat(beatId: String) {
        val project = _uiState.value.project ?: return
        val updatedBeats = _uiState.value.beats.filterNot { it.id == beatId }
        persistBeats(project, updatedBeats)
    }

    private fun persistBeats(project: Project, beats: List<BeatMarker>) {
        viewModelScope.launch {
            val updated = project.copy(beatMarkers = beats)
            db.projectDao().upsertProject(updated)
            _uiState.value = _uiState.value.copy(project = updated, beats = beats)
        }
    }

    /** Used while dragging a fragment edge on the timeline; magnet snap logic. */
    fun snapDragTime(rawTimeMs: Long): Long = beatEngine.snapToNearestBeat(rawTimeMs, _uiState.value.beats)

    fun dismissChangeEditGrid() {
        _uiState.value = _uiState.value.copy(showChangeEditGrid = false)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null, processingStage = ProcessingStage.IDLE)
    }
}

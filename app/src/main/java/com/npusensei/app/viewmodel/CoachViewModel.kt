package com.npusensei.app.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.npusensei.app.NpuSenseiApplication
import com.npusensei.app.camera.FrameAnalyzer
import com.npusensei.app.circuit.BlueprintStep
import com.npusensei.app.circuit.BreadboardMapper
import com.npusensei.app.circuit.CircuitBlueprint
import com.npusensei.app.circuit.StepEngine
import com.npusensei.app.circuit.StepStatus
import com.npusensei.app.gemma.PromptBuilder
import com.npusensei.app.ml.SceneState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class CoachViewModel(app: Application) : AndroidViewModel(app) {

    private val container = app as NpuSenseiApplication
    private val stepEngine = StepEngine()
    private val coachMutex = Mutex()
    private var readySince: Long = 0L
    private var coachJob: Job? = null
    private var lastGemmaStep: Int = -1

    val analyzer: FrameAnalyzer = FrameAnalyzer(container.detector)

    private val _uiState = MutableStateFlow(CoachUiState())
    val uiState: StateFlow<CoachUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val first = withContext(Dispatchers.IO) {
                container.blueprints.loadAll().firstOrNull()
            }
            if (first != null) selectBlueprint(first)
        }

        analyzer.scene
            .onEach(::onSceneChanged)
            .launchIn(viewModelScope)

        combine(analyzer.liveDetections, _uiState) { dets, ui -> dets to ui }
            .distinctUntilChanged()
            .onEach { (dets, ui) ->
                val mapper = ui.breadboardMapper
                val bb = dets.firstOrNull { it.label == "breadboard" }
                val step = ui.currentStep
                val highlight = step?.highlight

                if (mapper == null || bb == null || highlight == null) {
                    _uiState.update { it.copy(highlightPx = null, highlightBox = null) }
                    return@onEach
                }

                if (highlight.type == "hole" && highlight.row != null && highlight.col != null) {
                    val center = mapper.holeCenter(bb.box, highlight.row, highlight.col)
                    _uiState.update { it.copy(highlightPx = center, highlightBox = null) }
                } else if (highlight.type == "component" && highlight.target != null) {
                    val target = dets.firstOrNull { it.label == highlight.target }
                    _uiState.update { it.copy(highlightBox = target?.box, highlightPx = null) }
                } else {
                    _uiState.update { it.copy(highlightPx = null, highlightBox = null) }
                }
            }
            .launchIn(viewModelScope)
    }

    fun selectBlueprint(bp: CircuitBlueprint) {
        readySince = 0L
        lastGemmaStep = -1
        _uiState.update {
            it.copy(
                blueprint = bp,
                stepIndex = 0,
                startedAtMs = System.currentTimeMillis(),
                breadboardMapper = bp.breadboardGeometry?.let { g -> BreadboardMapper(g) },
                coachText = "Tap the ask button to get coaching help.",
                coachSource = null,
            )
        }
    }

    fun nextStep() {
        _uiState.update { state ->
            val bp = state.blueprint ?: return@update state
            val next = (state.stepIndex + 1).coerceAtMost(bp.steps.lastIndex)
            readySince = 0L
            state.copy(stepIndex = next, status = StepStatus.WaitingFor(emptyList()))
        }
    }

    fun previousStep() {
        _uiState.update { state ->
            val next = (state.stepIndex - 1).coerceAtLeast(0)
            readySince = 0L
            state.copy(stepIndex = next, status = StepStatus.WaitingFor(emptyList()))
        }
    }

    fun clearHighlights() {
        _uiState.update { it.copy(highlightPx = null, highlightBox = null) }
    }

    fun askCoach(question: String, frame: Bitmap? = null) {
        val state = _uiState.value
        val step = state.currentStep ?: return
        val bp = state.blueprint ?: return
        runCoach(bp, step, state.status, analyzer.scene.value, frame, userQuestion = question)
    }

    private fun onSceneChanged(scene: SceneState) {
        val state = _uiState.value
        val bp = state.blueprint ?: return
        val step = state.currentStep ?: return

        val newStatus = stepEngine.evaluate(step, scene)
        val now = System.currentTimeMillis()

        if (newStatus == StepStatus.Ready) {
            if (readySince == 0L) readySince = now
            if (now - readySince >= StepEngine.READY_HOLD_MS) {
                readySince = 0L
                _uiState.update { it.copy(status = StepStatus.Complete) }
                return
            }
        } else {
            readySince = 0L
        }

        _uiState.update { it.copy(status = newStatus) }
    }

    private fun runCoach(
        blueprint: CircuitBlueprint,
        step: BlueprintStep,
        status: StepStatus,
        scene: SceneState,
        frame: Bitmap?,
        userQuestion: String? = null,
    ) {
        val engine = container.gemmaEngine
        if (!engine.initialized) {
            _uiState.update {
                it.copy(
                    coachText = "Gemma is still loading — hang tight…",
                    coachSource = CoachSource.LITERT_LM,
                    coachThinking = true,
                )
            }
            return
        }

        val currentStep = _uiState.value.stepIndex

        coachJob?.cancel()
        coachJob = viewModelScope.launch(Dispatchers.IO) {
            coachMutex.withLock {
                _uiState.update { it.copy(coachThinking = true) }
                try {
                    if (lastGemmaStep != currentStep) {
                        engine.resetConversation()
                        engine.startConversation(PromptBuilder.SYSTEM_PROMPT)
                        lastGemmaStep = currentStep
                    }

                    val prompt = PromptBuilder.buildUserPrompt(
                        blueprint, step, status, scene, userQuestion,
                    )
                    val started = System.currentTimeMillis()
                    val chunks = mutableListOf<String>()

                    engine.sendMessage(prompt)
                        .catch { e ->
                            Log.e(TAG, "Gemma stream error", e)
                            chunks.add("(error: ${e.message})")
                        }
                        .collect { chunk ->
                            chunks.add(chunk)
                            _uiState.update {
                                it.copy(
                                    coachText = chunks.joinToString(""),
                                    coachSource = CoachSource.LITERT_LM,
                                    coachThinking = true,
                                )
                            }
                        }

                    val elapsed = System.currentTimeMillis() - started
                    _uiState.update {
                        it.copy(
                            coachText = chunks.joinToString("").ifBlank { "…" },
                            coachSource = CoachSource.LITERT_LM,
                            coachLatencyMs = elapsed,
                            coachThinking = false,
                        )
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Coach call failed", t)
                    _uiState.update {
                        it.copy(
                            coachText = "(coach unavailable — ${t.message ?: "error"})",
                            coachThinking = false,
                        )
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        coachJob?.cancel()
    }

    companion object {
        private const val TAG = "CoachViewModel"
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(
                modelClass: Class<T>, extras: CreationExtras,
            ): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as Application
                return CoachViewModel(app) as T
            }
        }
    }
}

enum class CoachSource { LITERT_LM }

data class CoachUiState(
    val blueprint: CircuitBlueprint? = null,
    val stepIndex: Int = 0,
    val status: StepStatus = StepStatus.WaitingFor(emptyList()),
    val coachText: String = "",
    val coachSource: CoachSource? = null,
    val coachLatencyMs: Long = 0L,
    val coachThinking: Boolean = false,
    val breadboardMapper: BreadboardMapper? = null,
    val highlightPx: android.graphics.PointF? = null,
    val highlightBox: android.graphics.RectF? = null,
    val startedAtMs: Long = 0L,
) {
    val currentStep: BlueprintStep?
        get() = blueprint?.steps?.getOrNull(stepIndex)

    val isLastStep: Boolean
        get() = blueprint != null && stepIndex == blueprint.steps.lastIndex
}

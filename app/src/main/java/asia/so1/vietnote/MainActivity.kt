package asia.so1.vietnote

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- ASR

enum class AsrStatus { PREPARING, READY, RECORDING, ERROR }

data class AsrUiState(
    val status: AsrStatus = AsrStatus.PREPARING,
    val partialText: String = "",
    val segments: List<String> = emptyList(),
    val error: String? = null,
)

class MainViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(AsrUiState())
    val uiState: StateFlow<AsrUiState> = _uiState.asStateFlow()

    private var engine: AsrEngine? = null

    fun attachEngine(e: AsrEngine) {
        engine = e
        e.onReady = { _uiState.update { it.copy(status = AsrStatus.READY, error = null) } }
        e.onPartial = { text -> _uiState.update { it.copy(partialText = text) } }
        e.onFinal = { text ->
            _uiState.update { it.copy(segments = it.segments + text, partialText = "") }
        }
        e.onError = { msg ->
            _uiState.update { it.copy(status = AsrStatus.ERROR, error = msg) }
        }
        e.initAsync()
    }

    fun toggleRecording(hasPermission: Boolean, requestPermission: () -> Unit) {
        val st = _uiState.value.status
        if (st == AsrStatus.RECORDING) {
            engine?.stop()
            _uiState.update { it.copy(status = AsrStatus.READY, partialText = "") }
            return
        }
        if (st != AsrStatus.READY) return
        if (!hasPermission) {
            requestPermission()
            return
        }
        engine?.start()
        _uiState.update { it.copy(status = AsrStatus.RECORDING, error = null) }
    }

    fun clearAll() {
        _uiState.update { it.copy(segments = emptyList(), partialText = "") }
    }

    override fun onCleared() {
        engine?.release()
        super.onCleared()
    }
}

// ---------------------------------------------------------------- TTS

enum class TtsStatus { PREPARING, READY, GENERATING, ERROR }

data class TtsUiState(
    val status: TtsStatus = TtsStatus.PREPARING,
    val text: String = "",
    val speed: Float = 1.0f,
    val progressDone: Int = 0,
    val progressTotal: Int = 0,
    val files: List<TtsFile> = emptyList(),
    val playingUri: Uri? = null,
    val error: String? = null,
)

class TtsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(TtsUiState())
    val uiState: StateFlow<TtsUiState> = _uiState.asStateFlow()

    private var engine: TtsEngine? = null

    fun attachEngine(e: TtsEngine) {
        engine = e
        e.onReady = {
            _uiState.update { it.copy(status = TtsStatus.READY, error = null) }
            refreshFiles()
        }
        e.onError = { msg ->
            _uiState.update { it.copy(status = TtsStatus.ERROR, error = msg) }
        }
        e.onPlayFinished = {
            _uiState.update { it.copy(playingUri = null) }
        }
    }

    /** Nạp giọng đọc (chỉ chạy 1 lần, khi mở tab Đọc lần đầu). */
    fun ensureInit() {
        engine?.initAsync()
    }

    fun setText(t: String) {
        _uiState.update { it.copy(text = t) }
    }

    fun setSpeed(s: Float) {
        _uiState.update { it.copy(speed = s) }
    }

    fun generate() {
        val e = engine ?: return
        val st = _uiState.value
        if (st.status != TtsStatus.READY || st.text.isBlank()) return
        _uiState.update {
            it.copy(status = TtsStatus.GENERATING, progressDone = 0, progressTotal = 0)
        }
        viewModelScope.launch(Dispatchers.Default) {
            val uri = e.synthesize(st.text, st.speed) { done, total ->
                _uiState.update { it.copy(progressDone = done, progressTotal = total) }
            }
            _uiState.update {
                it.copy(
                    status = if (uri != null) TtsStatus.READY else TtsStatus.ERROR,
                    progressDone = 0,
                    progressTotal = 0,
                )
            }
            if (uri != null) refreshFiles()
        }
    }

    fun refreshFiles() {
        viewModelScope.launch(Dispatchers.Default) {
            val files = engine?.listFiles() ?: emptyList()
            _uiState.update { it.copy(files = files) }
        }
    }

    fun togglePlay(uri: Uri) {
        val playing = engine?.togglePlay(uri) ?: false
        _uiState.update { it.copy(playingUri = if (playing) uri else null) }
    }

    fun deleteFile(uri: Uri) {
        viewModelScope.launch(Dispatchers.Default) {
            if (engine?.deleteFile(uri) == true) refreshFiles()
        }
    }

    override fun onCleared() {
        engine?.release()
        super.onCleared()
    }
}

// ---------------------------------------------------------------- Activity

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private val ttsViewModel: TtsViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.toggleRecording(true) {}
        } else {
            Toast.makeText(this, "Cần quyền micro để nghe", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val engine = AsrEngine(this)
        viewModel.attachEngine(engine)
        val ttsEngine = TtsEngine(this)
        ttsViewModel.attachEngine(ttsEngine)
        setContent {
            VietNoteApp(
                asrState = viewModel.uiState,
                ttsState = ttsViewModel.uiState,
                onMicClick = {
                    viewModel.toggleRecording(hasMicPermission()) {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onClear = { viewModel.clearAll() },
                onCopyAll = { copyAll() },
                onShare = { shareAll() },
                onTtsTabSelected = { ttsViewModel.ensureInit() },
                onTtsTextChange = { ttsViewModel.setText(it) },
                onTtsSpeedChange = { ttsViewModel.setSpeed(it) },
                onTtsGenerate = { ttsViewModel.generate() },
                onTtsPlay = { ttsViewModel.togglePlay(it) },
                onTtsShare = { shareFile(it) },
                onTtsDelete = { ttsViewModel.deleteFile(it) },
            )
        }
    }

    private fun hasMicPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun fullText(): String {
        val s = viewModel.uiState.value
        return (s.segments + listOf(s.partialText))
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    private fun copyAll() {
        val text = fullText()
        if (text.isBlank()) {
            Toast.makeText(this, "Chưa có chữ nào", Toast.LENGTH_SHORT).show()
            return
        }
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("VietNote ASR", text))
        Toast.makeText(this, "Đã sao chép", Toast.LENGTH_SHORT).show()
    }

    private fun shareAll() {
        val text = fullText()
        if (text.isBlank()) {
            Toast.makeText(this, "Chưa có chữ nào", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "Chia sẻ bản ghi"))
    }

    private fun shareFile(uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/mpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Chia sẻ file MP3"))
    }
}

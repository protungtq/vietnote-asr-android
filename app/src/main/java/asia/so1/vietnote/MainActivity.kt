package asia.so1.vietnote

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

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
        setContent {
            VietNoteApp(
                uiState = viewModel.uiState,
                onMicClick = {
                    viewModel.toggleRecording(hasMicPermission()) {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onClear = { viewModel.clearAll() },
                onCopyAll = { copyAll() },
                onShare = { shareAll() },
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
}

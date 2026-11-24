package asia.so1.vietnote

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val VietNoteRed = Color(0xFFC62828)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VietNoteApp(
    asrState: StateFlow<AsrUiState>,
    ttsState: StateFlow<TtsUiState>,
    onMicClick: () -> Unit,
    onClear: () -> Unit,
    onCopyAll: () -> Unit,
    onShare: () -> Unit,
    onTtsTabSelected: () -> Unit,
    onTtsTextChange: (String) -> Unit,
    onTtsSpeedChange: (Float) -> Unit,
    onTtsGenerate: () -> Unit,
    onTtsPlay: (Uri) -> Unit,
    onTtsShare: (Uri) -> Unit,
    onTtsDelete: (Uri) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = VietNoteRed,
            onPrimary = Color.White,
        ),
    ) {
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Column {
                                Text("VietNote", fontWeight = FontWeight.Bold)
                                Text(
                                    "Nghe giọng nói • Đọc văn bản • Offline",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                    TabRow(selectedTabIndex = tab) {
                        Tab(
                            selected = tab == 0,
                            onClick = { tab = 0 },
                            text = { Text("Nghe") },
                            icon = { Icon(Icons.Filled.Mic, contentDescription = null) },
                        )
                        Tab(
                            selected = tab == 1,
                            onClick = {
                                tab = 1
                                onTtsTabSelected()
                            },
                            text = { Text("Đọc") },
                            icon = { Icon(Icons.Filled.VolumeUp, contentDescription = null) },
                        )
                    }
                }
            },
        ) { padding ->
            if (tab == 0) {
                AsrScreen(
                    modifier = Modifier.padding(padding),
                    uiState = asrState,
                    onMicClick = onMicClick,
                    onClear = onClear,
                    onCopyAll = onCopyAll,
                    onShare = onShare,
                )
            } else {
                TtsScreen(
                    modifier = Modifier.padding(padding),
                    uiState = ttsState,
                    onTextChange = onTtsTextChange,
                    onSpeedChange = onTtsSpeedChange,
                    onGenerate = onTtsGenerate,
                    onPlay = onTtsPlay,
                    onShare = onTtsShare,
                    onDelete = onTtsDelete,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- tab Nghe

@Composable
private fun AsrScreen(
    modifier: Modifier,
    uiState: StateFlow<AsrUiState>,
    onMicClick: () -> Unit,
    onClear: () -> Unit,
    onCopyAll: () -> Unit,
    onShare: () -> Unit,
) {
    val state by uiState.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(state.segments.size) {
        if (state.segments.isNotEmpty()) {
            listState.animateScrollToItem(state.segments.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "Model: VietNote-Zipformer-78M-Streaming (int8, 83 MB)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Engine: sherpa-onnx 2.0.3 • Chạy 100% trên máy, không cần mạng",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.status == AsrStatus.ERROR && state.error != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        state.error!!,
                        fontSize = 13.sp,
                        color = VietNoteRed,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (state.partialText.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFEBEE),
                ),
            ) {
                Text(
                    "…${state.partialText}",
                    modifier = Modifier.padding(12.dp),
                    fontSize = 18.sp,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (state.segments.isEmpty() && state.partialText.isBlank()) {
            Text(
                "Chưa có bản ghi nào. Bấm mic rồi nói tiếng Việt " +
                    "— kể cả câu pha tiếng Anh, app vẫn nghe được.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(state.segments) { idx, text ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "${idx + 1}. $text",
                            modifier = Modifier.padding(12.dp),
                            fontSize = 16.sp,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onCopyAll) {
                Icon(Icons.Filled.ContentCopy, contentDescription = null)
                Text(" Sao chép")
            }
            OutlinedButton(onClick = onShare) {
                Icon(Icons.Filled.Share, contentDescription = null)
                Text(" Chia sẻ")
            }
            OutlinedButton(onClick = onClear) {
                Icon(Icons.Filled.Clear, contentDescription = null)
                Text(" Xóa")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FloatingActionButton(
                onClick = onMicClick,
                modifier = Modifier.size(76.dp),
                containerColor = if (state.status == AsrStatus.RECORDING) {
                    Color(0xFF424242)
                } else {
                    VietNoteRed
                },
            ) {
                Icon(
                    imageVector = if (state.status == AsrStatus.RECORDING) {
                        Icons.Filled.Stop
                    } else {
                        Icons.Filled.Mic
                    },
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = Color.White,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when (state.status) {
                    AsrStatus.PREPARING -> "Đang chuẩn bị model…"
                    AsrStatus.READY -> "Bấm mic rồi nói"
                    AsrStatus.RECORDING -> "Đang nghe — bấm mic để dừng"
                    AsrStatus.ERROR -> "Có lỗi"
                },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ---------------------------------------------------------------- tab Đọc

@Composable
private fun TtsScreen(
    modifier: Modifier,
    uiState: StateFlow<TtsUiState>,
    onTextChange: (String) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onGenerate: () -> Unit,
    onPlay: (Uri) -> Unit,
    onShare: (Uri) -> Unit,
    onDelete: (Uri) -> Unit,
) {
    val state by uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "Giọng đọc: Piper vi-VN vais1000 (medium, 22050 Hz)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "File WAV lưu ở Download/VietNote • Chạy 100% trên máy",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (state.status == TtsStatus.ERROR && state.error != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        state.error!!,
                        fontSize = 13.sp,
                        color = VietNoteRed,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = state.text,
            onValueChange = onTextChange,
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            placeholder = { Text("Nhập hoặc dán văn bản tiếng Việt cần đọc…") },
            label = { Text("Văn bản (${state.text.length} ký tự)") },
        )

        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Tốc độ: ", fontSize = 14.sp)
            Text(
                "%.1fx".format(state.speed),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
            Slider(
                value = state.speed,
                onValueChange = onSpeedChange,
                valueRange = 0.5f..1.5f,
                steps = 9,
                modifier = Modifier.weight(1f),
            )
        }

        if (state.status == TtsStatus.GENERATING) {
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = {
                    if (state.progressTotal > 0) {
                        state.progressDone.toFloat() / state.progressTotal
                    } else {
                        0f
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Đang đọc đoạn ${state.progressDone}/${state.progressTotal}…",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onGenerate,
            enabled = state.status == TtsStatus.READY && state.text.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.VolumeUp, contentDescription = null)
            Text(
                when (state.status) {
                    TtsStatus.PREPARING -> " Đang chuẩn bị giọng đọc…"
                    TtsStatus.GENERATING -> " Đang tạo file WAV…"
                    else -> " Tạo file WAV"
                },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "File đã tạo",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (state.files.isEmpty()) {
            Text(
                "Chưa có file nào. Văn bản sau khi đọc sẽ lưu ở đây và " +
                    "trong thư mục Download/VietNote trên máy.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.files) { file ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    TtsEngine.shortName(file),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "${file.sizeBytes / 1024} KB • ${
                                        SimpleDateFormat(
                                            "dd/MM HH:mm",
                                            Locale.getDefault(),
                                        ).format(Date(file.dateAdded * 1000))
                                    }",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { onPlay(file.uri) }) {
                                Icon(
                                    imageVector = if (state.playingUri == file.uri) {
                                        Icons.Filled.Stop
                                    } else {
                                        Icons.Filled.PlayArrow
                                    },
                                    contentDescription = "Nghe thử",
                                )
                            }
                            IconButton(onClick = { onShare(file.uri) }) {
                                Icon(Icons.Filled.Share, contentDescription = "Chia sẻ")
                            }
                            IconButton(onClick = { onDelete(file.uri) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Xóa")
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

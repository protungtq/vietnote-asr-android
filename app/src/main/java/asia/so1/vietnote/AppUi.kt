package asia.so1.vietnote

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.StateFlow

private val VietNoteRed = Color(0xFFC62828)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VietNoteApp(
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

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = VietNoteRed,
            onPrimary = Color.White,
        ),
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("VietNote ASR", fontWeight = FontWeight.Bold)
                            Text(
                                "Nhận dạng giọng nói tiếng Việt • Offline",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
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
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
            ) {
                // Thẻ trạng thái model
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

                // Chữ đang nói dở (partial)
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

                // Các câu đã chốt
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
            }
        }
    }
}

package asia.so1.vietnote

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Nhận dạng giọng nói trực tiếp từ micro bằng model VietNote streaming
 * (Zipformer transducer, bản int8 83 MB), chạy hoàn toàn trên máy
 * qua sherpa-onnx. Tiếng không rời khỏi thiết bị.
 */
class AsrEngine(private val context: Context) {

    var onReady: () -> Unit = {}
    var onPartial: (String) -> Unit = {}
    var onFinal: (String) -> Unit = {}
    var onError: (String) -> Unit = {}

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var recognizer: OnlineRecognizer? = null
    private var audioRecord: AudioRecord? = null
    private var stream: OnlineStream? = null
    private var recordingJob: Job? = null

    @Volatile
    var isRecording: Boolean = false
        private set

    @Volatile
    var isReady: Boolean = false
        private set

    /** Nạp model (chạy nền vì mất vài giây). */
    fun initAsync() {
        scope.launch {
            try {
                val config = OnlineRecognizerConfig(
                    featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
                    modelConfig = OnlineModelConfig(
                        transducer = OnlineTransducerModelConfig(
                            encoder = "model/encoder.int8.onnx",
                            decoder = "model/decoder.int8.onnx",
                            joiner = "model/joiner.int8.onnx",
                        ),
                        tokens = "model/tokens.txt",
                        numThreads = 2,
                    ),
                    enableEndpoint = true,
                    decodingMethod = "greedy_search",
                )
                recognizer = OnlineRecognizer(assetManager = context.assets, config = config)
                isReady = true
                onReady()
            } catch (e: Exception) {
                Log.e(TAG, "Không khởi tạo được model", e)
                onError("Không khởi tạo được model: ${e.message}")
            }
        }
    }

    fun start() {
        val rec = recognizer
        if (rec == null) {
            onError("Model chưa sẵn sàng")
            return
        }
        if (isRecording) return
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuf <= 0) {
            onError("Máy không hỗ trợ thu âm 16 kHz")
            return
        }
        val ar = AudioRecord(
            MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf * 2,
        )
        if (ar.state != AudioRecord.STATE_INITIALIZED) {
            ar.release()
            onError("Không mở được micro")
            return
        }
        audioRecord = ar
        ar.startRecording()
        isRecording = true
        val s = rec.createStream()
        stream = s
        recordingJob = scope.launch {
            // Đọc từng đoạn 100 ms, đẩy vào recognizer, lấy chữ từng phần.
            val buffer = ShortArray(SAMPLE_RATE / 10)
            try {
                while (isRecording) {
                    val ret = ar.read(buffer, 0, buffer.size)
                    if (ret > 0) {
                        val samples = FloatArray(ret) { buffer[it] / 32768f }
                        s.acceptWaveform(samples, SAMPLE_RATE)
                        while (rec.isReady(s)) rec.decode(s)
                        val text = rec.getResult(s).text
                        if (text.isNotBlank()) onPartial(text)
                        if (rec.isEndpoint(s)) {
                            if (text.isNotBlank()) onFinal(text)
                            rec.reset(s)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi khi nghe", e)
                onError("Lỗi khi nghe: ${e.message}")
            }
        }
    }

    /** Dừng nghe; đẩy thêm khoảng lặng để chốt câu cuối đang nói dở. */
    fun stop() {
        if (!isRecording) return
        isRecording = false
        scope.launch {
            try {
                recordingJob?.join()
            } catch (_: Exception) {
            }
            recordingJob = null
            val rec = recognizer
            val s = stream
            try {
                if (rec != null && s != null) {
                    val silence = FloatArray((0.8f * SAMPLE_RATE).toInt())
                    s.acceptWaveform(silence, SAMPLE_RATE)
                    while (rec.isReady(s)) rec.decode(s)
                    val text = rec.getResult(s).text
                    if (text.isNotBlank()) onFinal(text)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Lỗi khi chốt câu cuối", e)
            } finally {
                try {
                    s?.release()
                } catch (_: Exception) {
                }
                stream = null
                try {
                    audioRecord?.stop()
                    audioRecord?.release()
                } catch (_: Exception) {
                }
                audioRecord = null
            }
        }
    }

    fun release() {
        try {
            isRecording = false
        } catch (_: Exception) {
        }
        try {
            recordingJob?.cancel()
        } catch (_: Exception) {
        }
        try {
            stream?.release()
        } catch (_: Exception) {
        }
        try {
            audioRecord?.release()
        } catch (_: Exception) {
        }
        try {
            recognizer?.release()
        } catch (_: Exception) {
        }
        scope.cancel()
    }

    companion object {
        private const val TAG = "VietNoteAsr"
        private const val SAMPLE_RATE = 16000
    }
}

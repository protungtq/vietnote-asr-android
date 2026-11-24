package asia.so1.vietnote

import android.content.ContentValues
import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.github.csukuangfj.piper.phonemize.piperPhonemizeInitialize
import com.github.csukuangfj.piper.phonemize.piperPhonemizeText
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Một file MP3 đã tạo, nằm ở Download/VietNote. */
data class TtsFile(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val dateAdded: Long,
)

private const val TAG = "VietNoteTts"
private const val TTS_MODEL_ASSET = "tts/vi_VN-vais1000-medium.onnx"
private const val TTS_FOLDER = "Download/VietNote"

/**
 * Đọc văn bản tiếng Việt thành MP3, chạy 100% trên máy:
 *
 * - Giọng: Piper VITS `vi_VN-vais1000-medium` (1 giọng nữ, 22050 Hz),
 *   tải từ rhasspy/piper-voices — giấy phép MIT.
 * - Chuyển văn bản thành âm vị bằng piper-phonemize (espeak-ng).
 * - Âm thanh do sherpa-onnx (OfflineTts) sinh ra, mã hóa MP3 128 kbps
 *   bằng libmp3lame (xem Mp3Encoder).
 *
 * File .onnx.json phải nằm cạnh file .onnx trong assets (sherpa-onnx đọc
 * bảng ánh xạ âm vị từ đó); cả hai đều do workflow tải về lúc build.
 */
class TtsEngine(private val context: Context) {
    private var tts: OfflineTts? = null
    private var initStarted = false
    private var mediaPlayer: MediaPlayer? = null
    private var playingUri: Uri? = null

    var onReady: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    val isReady: Boolean get() = tts != null

    /** Nạp model ở luồng nền. Gọi 1 lần khi mở tab Đọc lần đầu. */
    fun initAsync() {
        if (initStarted) return
        initStarted = true
        Thread({
            try {
                piperPhonemizeInitialize(context.applicationContext)
                val config = OfflineTtsConfig(
                    model = OfflineTtsModelConfig(
                        vits = OfflineTtsVitsModelConfig(model = TTS_MODEL_ASSET),
                        numThreads = 2,
                    ),
                )
                tts = OfflineTts(context.assets, config)
                Log.i(TAG, "TTS ready, lang=${tts?.lang()}, sr=${tts?.sampleRate()}")
                onReady?.invoke()
            } catch (e: Exception) {
                Log.e(TAG, "TTS init failed", e)
                onError?.invoke("Không nạp được giọng đọc: ${e.message}")
            }
        }, "tts-init").start()
    }

    fun sampleRate(): Int = tts?.sampleRate() ?: 22050

    /**
     * Đọc [text] thành file MP3, lưu ở Download/VietNote.
     * [speed]: tốc độ đọc (0.5–1.5, mặc định 1.0).
     * [onProgress]: (đoạn đã xong, tổng số đoạn).
     * Trả về Uri của file, hoặc null nếu lỗi (xem [onError]).
     */
    fun synthesize(
        text: String,
        speed: Float,
        onProgress: (done: Int, total: Int) -> Unit,
    ): Uri? {
        val engine = tts ?: run {
            onError?.invoke("Giọng đọc chưa sẵn sàng")
            return null
        }
        val chunks = splitIntoChunks(text)
        if (chunks.isEmpty()) return null
        return try {
            val encoder = Mp3Encoder(engine.sampleRate(), bitrateKbps = 128)
            val mp3 = java.io.ByteArrayOutputStream()
            for ((i, chunk) in chunks.withIndex()) {
                val audio = generateChunk(engine, chunk, speed)
                if (audio.samples.isNotEmpty()) {
                    val bytes = encoder.encode(audio.samples)
                    if (bytes.isNotEmpty()) mp3.write(bytes)
                }
                onProgress(i + 1, chunks.size)
            }
            val tail = encoder.flush()
            if (tail.isNotEmpty()) mp3.write(tail)
            encoder.close()
            saveToDownloads(mp3.toByteArray())
        } catch (e: Exception) {
            Log.e(TAG, "synthesize failed", e)
            onError?.invoke("Đọc thất bại: ${e.message}")
            null
        }
    }

    private fun generateChunk(engine: OfflineTts, chunk: String, speed: Float) =
        engine.generateWithConfig(
            text = "",
            config = GenerationConfig(
                phonemeCodepoints = phonemize(chunk),
                speed = speed,
            ),
        )

    /** Chuyển 1 đoạn văn bản thành mảng âm vị (mỗi câu 1 mảng codepoint). */
    private fun phonemize(text: String): Array<IntArray> {
        // tts.lang() trả về dạng "vi_VN"; espeak-ng chỉ nhận "vi".
        val lang = tts?.lang().orEmpty().let {
            when {
                it.startsWith("vi", ignoreCase = true) -> "vi"
                it.isBlank() -> "vi"
                else -> it
            }
        }
        val result = piperPhonemizeText(text, lang)
            ?: throw RuntimeException("Không chuyển được văn bản thành âm vị")
        return try {
            Array(result.numSentences) { i -> result.getPhonemes(i) }
        } finally {
            result.close()
        }
    }

    /** Chia văn bản thành từng đoạn ngắn (tối đa ~300 ký tự) để đọc ổn định. */
    private fun splitIntoChunks(text: String): List<String> {
        val sentences = text
            .split(Regex("(?<=[.!?…\\n])\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val chunks = mutableListOf<String>()
        for (s in sentences) {
            if (s.length <= 300) {
                chunks.add(s)
            } else {
                // Câu quá dài: cắt ở khoảng trắng gần mốc 300 nhất.
                var rest = s
                while (rest.length > 300) {
                    val cut = rest.lastIndexOf(' ', 300).takeIf { it > 100 } ?: 300
                    chunks.add(rest.substring(0, cut).trim())
                    rest = rest.substring(cut).trim()
                }
                if (rest.isNotBlank()) chunks.add(rest)
            }
        }
        return chunks
    }

    private fun saveToDownloads(mp3Bytes: ByteArray): Uri? {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val name = "VietNote-$stamp.mp3"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "audio/mpeg")
            put(MediaStore.Downloads.RELATIVE_PATH, TTS_FOLDER)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI, values,
        ) ?: throw RuntimeException("Không tạo được file trong Download")
        resolver.openOutputStream(uri)?.use { it.write(mp3Bytes) }
            ?: throw RuntimeException("Không ghi được file MP3")
        return uri
    }

    /** Liệt kê các file MP3 đã tạo trong Download/VietNote, mới nhất trước. */
    fun listFiles(): List<TtsFile> {
        // RELATIVE_PATH chỉ có từ Android 10 (API 29).
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()
        val resolver = context.contentResolver
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.SIZE,
            MediaStore.Downloads.DATE_ADDED,
        )
        val out = mutableListOf<TtsFile>()
        try {
            resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Downloads.RELATIVE_PATH} = ?",
                arrayOf("$TTS_FOLDER/"),
                "${MediaStore.Downloads.DATE_ADDED} DESC",
            )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)
            val dateCol = c.getColumnIndexOrThrow(MediaStore.Downloads.DATE_ADDED)
            while (c.moveToNext()) {
                val uri = Uri.withAppendedPath(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    c.getLong(idCol).toString(),
                )
                out.add(
                    TtsFile(
                        uri = uri,
                        name = c.getString(nameCol),
                        sizeBytes = c.getLong(sizeCol),
                        dateAdded = c.getLong(dateCol),
                    ),
                )
            }
        }
        } catch (e: Exception) {
            Log.e(TAG, "listFiles failed", e)
        }
        return out
    }

    fun deleteFile(uri: Uri): Boolean {
        if (playingUri == uri) stopPlaying()
        return context.contentResolver.delete(uri, null, null) > 0
    }

    /** Phát/dừng nghe thử 1 file. Trả về true nếu đang phát file đó. */
    fun togglePlay(uri: Uri): Boolean {
        if (playingUri == uri) {
            stopPlaying()
            return false
        }
        stopPlaying()
        return try {
            mediaPlayer = MediaPlayer.create(context, uri)?.also {
                it.setOnCompletionListener {
                    stopPlaying()
                    onPlayFinished?.invoke()
                }
                it.start()
            }
            playingUri = uri
            true
        } catch (e: Exception) {
            Log.e(TAG, "play failed", e)
            false
        }
    }

    /** Gọi khi file đang nghe thử phát hết (để UI bỏ trạng thái đang phát). */
    var onPlayFinished: (() -> Unit)? = null

    fun isPlaying(uri: Uri): Boolean = playingUri == uri

    fun stopPlaying() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        playingUri = null
    }

    fun release() {
        stopPlaying()
        tts?.release()
        tts = null
    }

    companion object {
        /** Tên file hiển thị gọn, bỏ tiền tố VietNote- và đuôi .mp3. */
        fun shortName(file: TtsFile): String =
            file.name.removePrefix("VietNote-").removeSuffix(".mp3")
    }
}

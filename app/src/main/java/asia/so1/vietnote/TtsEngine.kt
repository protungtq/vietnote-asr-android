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

/** Một file WAV đã tạo, nằm ở Download/VietNote. */
data class TtsFile(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val dateAdded: Long,
)

private const val TAG = "VietNoteTts"
private const val TTS_MODEL_ASSET = "tts/vi_VN-vais1000-medium.onnx"
private const val TTS_TOKENS_ASSET = "tts/tokens.txt"
private const val TTS_FOLDER = "Download/VietNote"

/**
 * Đọc văn bản tiếng Việt thành file WAV, chạy 100% trên máy:
 *
 * - Giọng: Piper VITS `vi_VN-vais1000-medium` (1 giọng nữ, 22050 Hz),
 *   tải từ rhasspy/piper-voices — giấy phép MIT.
 * - Chuyển văn bản thành âm vị bằng piper-phonemize (espeak-ng).
 * - Âm thanh do sherpa-onnx (OfflineTts) sinh ra, ghi thẳng WAV 16-bit
 *   PCM — không cần mã hóa native (bản MP3 dùng libmp3lame đã bỏ vì
 *   build cứ gãy; Mp3Encoder.kt và app/src/main/cpp/ giờ là code chết,
 *   chưa xóa khỏi repo).
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
            debugLogInit()
            debugLog("init start")
            // Tách 2 bước ra để biết gãy ở đâu: nếu app out giữa chừng thì
            // mở Download/VietNote/VietNote-debug.log xem dòng cuối cùng.
            try {
                debugLog("piperPhonemizeInitialize start")
                piperPhonemizeInitialize(context.applicationContext)
                debugLog("piperPhonemizeInitialize done")
            } catch (t: Throwable) {
                Log.e(TAG, "piper init failed", t)
                debugLog("piperPhonemizeInitialize FAILED: ${t.message}")
                onError?.invoke("Không nạp được espeak-ng: ${t.message}")
                return@Thread
            }
            try {
                debugLog("OfflineTts create start")
                val config = OfflineTtsConfig(
                    model = OfflineTtsModelConfig(
                        // tokens.txt BẮT BUỘC: sherpa-onnx đổi âm vị thành id
                        // qua bảng này; không có là generate trả về 0 mẫu
                        // (file WAV trắng 44 byte). Sinh từ .onnx.json lúc
                        // build, bỏ 5 nguyên âm đôi 2 ký tự (C++ chỉ nhận
                        // 1 ký tự/dòng, gặp dòng 2 ký tự là exit() luôn).
                        vits = OfflineTtsVitsModelConfig(
                            model = TTS_MODEL_ASSET,
                            tokens = TTS_TOKENS_ASSET,
                        ),
                        numThreads = 2,
                    ),
                )
                tts = OfflineTts(context.assets, config)
                debugLog("OfflineTts create done, lang=${tts?.lang()}, sr=${tts?.sampleRate()}")
                Log.i(TAG, "TTS ready, lang=${tts?.lang()}, sr=${tts?.sampleRate()}")
                onReady?.invoke()
            } catch (t: Throwable) {
                Log.e(TAG, "TTS init failed", t)
                debugLog("OfflineTts create FAILED: ${t.message}")
                onError?.invoke("Không nạp được giọng đọc: ${t.message}")
            }
        }, "tts-init").start()
    }

    // ---------------------------------------------------------- nhật ký lỗi

    /** File log chẩn đoán trong Download/VietNote (1 file duy nhất, ghi đè
     *  mỗi lần nạp giọng đọc). Ghi log không bao giờ được làm crash app. */
    private var debugLogUri: Uri? = null

    private fun debugLogInit() {
        try {
            val resolver = context.contentResolver
            resolver.delete(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                "${MediaStore.Downloads.DISPLAY_NAME} = ?",
                arrayOf("VietNote-debug.log"),
            )
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "VietNote-debug.log")
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, TTS_FOLDER)
            }
            debugLogUri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        } catch (_: Exception) {
            debugLogUri = null
        }
    }

    private fun debugLog(msg: String) {
        try {
            val uri = debugLogUri ?: return
            val line = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date()) +
                " " + msg + "\n"
            context.contentResolver.openOutputStream(uri, "wa")?.use {
                it.write(line.toByteArray(Charsets.UTF_8))
            }
        } catch (_: Exception) {
            // Ghi log thất bại thì thôi, không ảnh hưởng app.
        }
    }

    fun sampleRate(): Int = tts?.sampleRate() ?: 22050

    /**
     * Đọc [text] thành file WAV, lưu ở Download/VietNote.
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
            val sampleRate = engine.sampleRate()
            val pcm = java.io.ByteArrayOutputStream()
            for ((i, chunk) in chunks.withIndex()) {
                val audio = generateChunk(engine, chunk, speed)
                if (audio.samples.isNotEmpty()) {
                    pcm.write(floatToPcm16(audio.samples))
                }
                onProgress(i + 1, chunks.size)
            }
            saveToDownloads(wavBytes(pcm.toByteArray(), sampleRate))
        } catch (e: Exception) {
            Log.e(TAG, "synthesize failed", e)
            onError?.invoke("Đọc thất bại: ${e.message}")
            null
        }
    }

    /** Chuyển mẫu float [-1, 1] thành PCM 16-bit little-endian. */
    private fun floatToPcm16(samples: FloatArray): ByteArray {
        val out = ByteArray(samples.size * 2)
        for (i in samples.indices) {
            val v = (samples[i].coerceIn(-1f, 1f) * 32767f).toInt()
            out[i * 2] = (v and 0xFF).toByte()
            out[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }
        return out
    }

    /** Gắn header WAV 44 byte (PCM mono 16-bit) lên trước dữ liệu PCM. */
    private fun wavBytes(pcm: ByteArray, sampleRate: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream(44 + pcm.size)
        fun tag(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
        fun u32(v: Int) {
            out.write(v and 0xFF)
            out.write((v shr 8) and 0xFF)
            out.write((v shr 16) and 0xFF)
            out.write((v shr 24) and 0xFF)
        }
        fun u16(v: Int) {
            out.write(v and 0xFF)
            out.write((v shr 8) and 0xFF)
        }
        tag("RIFF")
        u32(36 + pcm.size)
        tag("WAVE")
        tag("fmt ")
        u32(16)
        u16(1) // PCM
        u16(1) // mono
        u32(sampleRate)
        u32(sampleRate * 2) // byte rate
        u16(2) // block align
        u16(16) // bits per sample
        tag("data")
        u32(pcm.size)
        out.write(pcm)
        return out.toByteArray()
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

    private fun saveToDownloads(wavBytes: ByteArray): Uri? {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val name = "VietNote-$stamp.wav"
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "audio/x-wav")
            put(MediaStore.Downloads.RELATIVE_PATH, TTS_FOLDER)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI, values,
        ) ?: throw RuntimeException("Không tạo được file trong Download")
        resolver.openOutputStream(uri)?.use { it.write(wavBytes) }
            ?: throw RuntimeException("Không ghi được file WAV")
        return uri
    }

    /** Liệt kê các file WAV đã tạo trong Download/VietNote, mới nhất trước. */
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
        /** Tên file hiển thị gọn, bỏ tiền tố VietNote- và đuôi .wav/.mp3. */
        fun shortName(file: TtsFile): String =
            file.name.removePrefix("VietNote-").removeSuffix(".wav").removeSuffix(".mp3")
    }
}

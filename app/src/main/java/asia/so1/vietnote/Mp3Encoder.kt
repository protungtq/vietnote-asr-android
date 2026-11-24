package asia.so1.vietnote

/**
 * Mã hóa âm thanh float (mono, [-1, 1]) thành MP3 qua libmp3lame (JNI).
 *
 * Dùng 1 phiên cho cả 1 file: [encode] cho từng đoạn, [flush] ở cuối.
 * Sau [close] không dùng lại được.
 */
class Mp3Encoder(sampleRate: Int, bitrateKbps: Int = 128) {
    private var handle: Long = 0L

    init {
        System.loadLibrary("vietnote-mp3")
        handle = nativeInit(sampleRate, bitrateKbps)
        require(handle != 0L) { "Không khởi tạo được bộ mã hóa MP3" }
    }

    /** Mã hóa một mảng mẫu, trả về các byte MP3 (có thể rỗng). */
    fun encode(samples: FloatArray): ByteArray {
        check(handle != 0L) { "Mp3Encoder đã đóng" }
        return nativeEncode(handle, samples) ?: ByteArray(0)
    }

    /** Đẩy nốt các byte MP3 còn kẹt trong bộ mã hóa (gọi 1 lần ở cuối). */
    fun flush(): ByteArray {
        check(handle != 0L) { "Mp3Encoder đã đóng" }
        return nativeFlush(handle) ?: ByteArray(0)
    }

    fun close() {
        if (handle != 0L) {
            nativeClose(handle)
            handle = 0L
        }
    }

    private external fun nativeInit(sampleRate: Int, bitrateKbps: Int): Long
    private external fun nativeEncode(handle: Long, samples: FloatArray): ByteArray?
    private external fun nativeFlush(handle: Long): ByteArray?
    private external fun nativeClose(handle: Long)
}

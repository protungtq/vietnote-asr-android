// Wrapper JNI cho libmp3lame: nhận mẫu âm thanh float [-1, 1] (mono),
// mã hóa thành MP3 (128 kbps, chất lượng 2) để lưu file.
//
// LAME là LGPL-2.1, mã nguồn tải từ SourceForge lúc build (xem workflow).
#include <jni.h>
#include <cstdint>
#include <vector>
#include "lame/lame.h"

// Số mẫu tối đa mỗi lần gọi lame_encode (giới hạn bộ nhớ đệm).
#define CHUNK_SAMPLES 8192

extern "C" JNIEXPORT jlong JNICALL
Java_asia_so1_vietnote_Mp3Encoder_nativeInit(
    JNIEnv* env, jobject /* this */, jint sampleRate, jint bitrateKbps) {
    lame_global_flags* gfp = lame_init();
    if (gfp == nullptr) return 0;
    lame_set_in_samplerate(gfp, sampleRate);
    lame_set_num_channels(gfp, 1);
    lame_set_brate(gfp, bitrateKbps);
    lame_set_quality(gfp, 2);
    lame_set_VBR(gfp, vbr_off);
    if (lame_init_params(gfp) < 0) {
        lame_close(gfp);
        return 0;
    }
    return reinterpret_cast<jlong>(gfp);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_asia_so1_vietnote_Mp3Encoder_nativeEncode(
    JNIEnv* env, jobject /* this */, jlong handle, jfloatArray samples) {
    lame_global_flags* gfp = reinterpret_cast<lame_global_flags*>(handle);
    if (gfp == nullptr) return nullptr;

    jsize numSamples = env->GetArrayLength(samples);
    jfloat* in = env->GetFloatArrayElements(samples, nullptr);
    if (in == nullptr) return nullptr;

    std::vector<unsigned char> out;
    const int mp3BufSize = (int)(1.25 * CHUNK_SAMPLES) + 7200;
    std::vector<unsigned char> mp3Buf(mp3BufSize);
    std::vector<short> pcm(CHUNK_SAMPLES);

    for (jsize offset = 0; offset < numSamples; offset += CHUNK_SAMPLES) {
        int n = numSamples - offset;
        if (n > CHUNK_SAMPLES) n = CHUNK_SAMPLES;
        for (int i = 0; i < n; i++) {
            float v = in[offset + i];
            if (v > 1.0f) v = 1.0f;
            if (v < -1.0f) v = -1.0f;
            pcm[i] = (short)(v * 32767.0f);
        }
        int m = lame_encode_buffer_interleaved(
            gfp, pcm.data(), n, mp3Buf.data(), mp3BufSize);
        if (m < 0) {
            env->ReleaseFloatArrayElements(samples, in, JNI_ABORT);
            return nullptr;
        }
        out.insert(out.end(), mp3Buf.begin(), mp3Buf.begin() + m);
    }

    env->ReleaseFloatArrayElements(samples, in, JNI_ABORT);

    jbyteArray result = env->NewByteArray((jsize)out.size());
    if (!out.empty()) {
        env->SetByteArrayRegion(
            result, 0, (jsize)out.size(),
            reinterpret_cast<jbyte*>(out.data()));
    }
    return result;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_asia_so1_vietnote_Mp3Encoder_nativeFlush(
    JNIEnv* env, jobject /* this */, jlong handle) {
    lame_global_flags* gfp = reinterpret_cast<lame_global_flags*>(handle);
    if (gfp == nullptr) return nullptr;

    const int mp3BufSize = 7200;
    std::vector<unsigned char> mp3Buf(mp3BufSize);
    int n = lame_encode_flush(gfp, mp3Buf.data(), mp3BufSize);

    jbyteArray result = nullptr;
    if (n > 0) {
        result = env->NewByteArray(n);
        env->SetByteArrayRegion(
            result, 0, n, reinterpret_cast<jbyte*>(mp3Buf.data()));
    }
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_asia_so1_vietnote_Mp3Encoder_nativeClose(
    JNIEnv* env, jobject /* this */, jlong handle) {
    lame_global_flags* gfp = reinterpret_cast<lame_global_flags*>(handle);
    if (gfp != nullptr) lame_close(gfp);
}

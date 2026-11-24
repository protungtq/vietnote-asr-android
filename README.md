# VietNote — Android

App Android tiếng Việt **chạy hoàn toàn offline trên máy**, 2 tab:

- **Nghe:** bấm mic, nói — chữ hiện ra ngay lúc đang nói.
- **Đọc:** nhập hoặc dán văn bản → tạo file MP3 để nghe / dùng lại.

- **Model nghe:** VietNote-Zipformer-78M-Streaming, bản int8 (83 MB), của
  [Kaisye / Phong Nguyen](https://github.com/kaisye/vietnote-asr) —
  giấy phép Apache-2.0. Nghe tốt tiếng Việt pha tiếng Anh, có dấu câu
  ở bản demo server (bản app này hiện chữ thô, chưa qua model chấm câu).
- **Giọng đọc:** Piper VITS `vi_VN-vais1000-medium` (60 MB, 1 giọng nữ,
  22050 Hz), từ [rhasspy/piper-voices](https://github.com/rhasspy/piper-voices)
  — giấy phép MIT.
- **Engine:** [sherpa-onnx 2.0.3](https://github.com/k2-fsa/sherpa-onnx)
  (k2-fsa) — giấy phép Apache-2.0.
- **Demo web của model nghe (chạy trên server, cần mạng):** https://asr.vietnote.vn
- **Model nghe trên Hugging Face:**
  https://huggingface.co/Kaisye/VietNote-Zipformer-78M-Streaming

## Tính năng (0.2.0)

### Tab Nghe

- Nghe trực tiếp từ micro, hiện chữ từng phần trong lúc nói.
- Tự chốt câu khi ngừng nói (endpoint detection), bấm dừng cũng chốt
  câu cuối đang nói dở.
- Sao chép toàn bộ / chia sẻ bản ghi / xóa.
- Không cần mạng, tiếng không rời khỏi thiết bị. Không cần GPU —
  model 83 MB chạy bằng 2 lõi CPU (đo trên 1 lõi đã nhanh gấp 27 lần
  thời gian thực).

### Tab Đọc

- Nhập hoặc dán văn bản tiếng Việt, chỉnh tốc độ đọc (0.5x–1.5x).
- Tạo file MP3 (128 kbps, mono) bằng libmp3lame, lưu ở
  `Download/VietNote` trên máy — mở bằng app Files, gửi Zalo/Telegram/
  Gmail… đều được.
- Nghe thử, chia sẻ, xóa file ngay trong app.
- Không cần mạng, chữ không rời khỏi thiết bị.

## Build

File nặng (AAR sherpa-onnx 50 MB + model nghe 83 MB + giọng đọc 60 MB
+ mã nguồn LAME) **không commit lên repo**; workflow GitHub Actions tự
tải về lúc build rồi đóng gói vào APK:

- `app/libs/sherpa-onnx-2.0.3.aar` — từ release v2.0.3 của k2-fsa/sherpa-onnx
- `app/src/main/assets/model/` — 4 file `streaming/int8` của model VietNote
  trên Hugging Face (`encoder.int8.onnx`, `decoder.int8.onnx`,
  `joiner.int8.onnx`, `tokens.txt`)
- `app/src/main/assets/tts/` — giọng đọc Piper
  (`vi_VN-vais1000-medium.onnx` + `.onnx.json`) từ rhasspy/piper-voices
- `app/src/main/cpp/lame/` — mã nguồn libmp3lame 3.100 từ SourceForge
  (chỉ phần mã hóa; build bằng CMake + NDK 27.2.12479018 do workflow cài)

APK build ra chỉ chứa ABI `arm64-v8a` (máy đích: OnePlus 15).
Keystore debug dùng chung với VDM Android để mọi bản thử cùng một chữ
ký, cài đè được — đây là khóa debug, không phải khóa phát hành.
File `debug.keystore` không commit lên repo (file nhị phân); workflow tự
khôi phục nó từ base64 nhúng trong `.github/workflows/build.yml`.
Build local cần copy file này về gốc repo trước.

## Giấy phép

Code của app này: MIT (xem `LICENSE`). Model VietNote và engine
sherpa-onnx: Apache-2.0; giọng đọc Piper: MIT; libmp3lame: LGPL-2.1
(mã nguồn lấy từ SourceForge lúc build) — giữ nguyên ghi nguồn của
tác giả.

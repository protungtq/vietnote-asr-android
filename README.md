# VietNote ASR — Android

App Android nhận dạng giọng nói tiếng Việt **chạy hoàn toàn offline trên máy**:
mở app, bấm mic, nói — chữ hiện ra ngay lúc đang nói.

- **Model:** VietNote-Zipformer-78M-Streaming, bản int8 (83 MB), của
  [Kaisye / Phong Nguyen](https://github.com/kaisye/vietnote-asr) —
  giấy phép Apache-2.0. Nghe tốt tiếng Việt pha tiếng Anh, có dấu câu
  ở bản demo server (bản app này hiện chữ thô, chưa qua model chấm câu).
- **Engine:** [sherpa-onnx 2.0.3](https://github.com/k2-fsa/sherpa-onnx)
  (k2-fsa) — giấy phép Apache-2.0.
- **Demo web (chạy trên server, cần mạng):** https://asr.vietnote.vn
- **Model trên Hugging Face:**
  https://huggingface.co/Kaisye/VietNote-Zipformer-78M-Streaming

## Tính năng (0.1.0)

- Nghe trực tiếp từ micro, hiện chữ từng phần trong lúc nói.
- Tự chốt câu khi ngừng nói (endpoint detection), bấm dừng cũng chốt
  câu cuối đang nói dở.
- Sao chép toàn bộ / chia sẻ bản ghi / xóa.
- Không cần mạng, tiếng không rời khỏi thiết bị. Không cần GPU —
  model 83 MB chạy bằng 2 lõi CPU (đo trên 1 lõi đã nhanh gấp 27 lần
  thời gian thực).

## Build

File nặng (AAR sherpa-onnx 50 MB + model 83 MB) **không commit lên repo**;
workflow GitHub Actions tự tải về lúc build rồi đóng gói vào APK:

- `app/libs/sherpa-onnx-2.0.3.aar` — từ release v2.0.3 của k2-fsa/sherpa-onnx
- `app/src/main/assets/model/` — 4 file `streaming/int8` của model VietNote
  trên Hugging Face (`encoder.int8.onnx`, `decoder.int8.onnx`,
  `joiner.int8.onnx`, `tokens.txt`)

APK build ra chỉ chứa ABI `arm64-v8a` (máy đích: OnePlus 15).
Keystore debug dùng chung với VDM Android để mọi bản thử cùng một chữ
ký, cài đè được — đây là khóa debug, không phải khóa phát hành.
File `debug.keystore` không commit lên repo (file nhị phân); workflow tự
khôi phục nó từ base64 nhúng trong `.github/workflows/build.yml`.
Build local cần copy file này về gốc repo trước.

## Giấy phép

Code của app này: MIT (xem `LICENSE`). Model VietNote và engine
sherpa-onnx: Apache-2.0, giữ nguyên ghi nguồn của tác giả.

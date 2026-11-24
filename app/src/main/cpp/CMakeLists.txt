# Build libmp3lame (chỉ phần mã hóa) + wrapper JNI cho app VietNote.
# Mã nguồn lame do workflow tải từ SourceForge về app/src/main/cpp/lame/
# (thư mục libmp3lame) lúc build, không commit lên repo.
cmake_minimum_required(VERSION 3.22.1)

project("vietnote-mp3")

# LAME chỉ mã hóa, không cần phần giải mã (mpglib).
add_library(
    mp3lame
    STATIC
    lame/bitstream.c
    lame/encoder.c
    lame/fft.c
    lame/gain_analysis.c
    lame/id3tag.c
    lame/lame.c
    lame/newmdct.c
    lame/presets.c
    lame/psymodel.c
    lame/quantize.c
    lame/quantize_pvt.c
    lame/reservoir.c
    lame/set_get.c
    lame/tables.c
    lame/takehiro.c
    lame/util.c
    lame/vbrquantize.c
    lame/VbrTag.c
    lame/version.c
)

# config.h (tự viết, tối giản cho Android) nằm cùng thư mục này.
target_include_directories(mp3lame PUBLIC lame ${CMAKE_CURRENT_SOURCE_DIR})
target_compile_definitions(mp3lame PRIVATE HAVE_CONFIG_H)
# LAME 3.100 dùng kiểu ieee754_float32_t trong util.c/util.h (hàm fast_log2)
# nhưng quên typedef cho nó — định nghĩa thành float (đúng bản chất:
# số thực 32-bit chuẩn IEEE-754). Không sửa mã nguồn LAME, chỉ thêm
# định nghĩa lúc biên dịch.
target_compile_definitions(mp3lame PRIVATE ieee754_float32_t=float)
target_compile_options(mp3lame PRIVATE -O3)

add_library(vietnote-mp3 SHARED mp3_encoder.cpp)

target_include_directories(
    vietnote-mp3
    PRIVATE
    lame
    ${CMAKE_CURRENT_SOURCE_DIR}
)
target_compile_definitions(vietnote-mp3 PRIVATE HAVE_CONFIG_H)

find_library(log-lib log)

target_link_libraries(vietnote-mp3 mp3lame ${log-lib})

/* config.h tối giản cho libmp3lame 3.100 trên Android (arm64).
 *
 * Bình thường file này do ./configure sinh ra; ở đây viết tay để build
 * bằng CMake trong Gradle, khỏi chạy autotools trên máy build.
 * Chỉ bật các tính năng C chuẩn có sẵn trong NDK, KHÔNG bật mã ASM x86
 * (XMMINTRIN, Takehiro IEEE754 hack) vì máy đích là ARM.
 */
#ifndef LAME_CONFIG_H
#define LAME_CONFIG_H

#define STDC_HEADERS 1
#define HAVE_LIMITS_H 1
#define HAVE_STDLIB_H 1
#define HAVE_STRING_H 1
#define HAVE_SYS_TYPES_H 1
#define HAVE_SYS_STAT_H 1
#define HAVE_INTTYPES_H 1
#define HAVE_STDINT_H 1
#define HAVE_MALLOC_H 1
#define HAVE_ERRNO_H 1
#define HAVE_UNISTD_H 1
#define HAVE_FCNTL_H 1
#define HAVE_MATH_H 1
#define HAVE_MEMCPY 1
#define HAVE_MEMSET 1
#define HAVE_STRCHR 1
#define HAVE_STRTOL 1
#define HAVE_STRTOUL 1
#define HAVE_STRDUP 1

#define SIZEOF_INT 4
#define SIZEOF_LONG 8
#define SIZEOF_SHORT 2
#define SIZEOF_FLOAT 4
#define SIZEOF_DOUBLE 8
#define SIZEOF_UNSIGNED_INT 4
#define SIZEOF_UNSIGNED_LONG 8
#define SIZEOF_UNSIGNED_SHORT 2
#define SIZEOF_UNSIGNED_LONG_LONG 8
#define SIZEOF_LONG_LONG 8

#define HAVE_INT8_T 1
#define HAVE_UINT8_T 1
#define HAVE_INT16_T 1
#define HAVE_UINT16_T 1
#define HAVE_INT32_T 1
#define HAVE_UINT32_T 1
#define HAVE_INT64_T 1
#define HAVE_UINT64_T 1

#endif /* LAME_CONFIG_H */

cmake_minimum_required(VERSION 3.10)

# Force CMAKE_BUILD_TYPE to Release to compile both whisper and ggml with full speed optimizations
set(CMAKE_BUILD_TYPE Release CACHE STRING "Choose the type of build" FORCE)

project(whisper_app)

set(CMAKE_CXX_STANDARD 17)
set(WHISPER_LIB_DIR "D:/fos voice project/whisper.cpp")

# Define the source files with double quotes to support paths with spaces
set(
    SOURCE_FILES
    "${WHISPER_LIB_DIR}/src/whisper.cpp"
    jni.c
)

find_library(LOG_LIB log)

include(FetchContent)

function(build_library target_name)
    add_library(
        ${target_name}
        SHARED
        ${SOURCE_FILES}
    )

    target_compile_definitions(${target_name} PUBLIC GGML_USE_CPU)
    target_compile_definitions(${target_name} PRIVATE WHISPER_VERSION="1.0.0")

    if (${target_name} STREQUAL "whisper_v8fp16_va")
        target_compile_options(${target_name} PRIVATE -march=armv8.2-a+fp16)
        set(GGML_COMPILE_OPTIONS                      -march=armv8.2-a+fp16)
    elseif (${target_name} STREQUAL "whisper_vfpv4")
        target_compile_options(${target_name} PRIVATE -mfpu=neon-vfpv4)
        set(GGML_COMPILE_OPTIONS                      -mfpu=neon-vfpv4)
    endif ()

    # Always compile with optimization to prevent extremely slow JNI execution
    target_compile_options(${target_name} PRIVATE -O3)

    if (NOT ${CMAKE_BUILD_TYPE} STREQUAL "Debug")
        target_compile_options(${target_name} PRIVATE -fvisibility=hidden -fvisibility-inlines-hidden)
        target_compile_options(${target_name} PRIVATE -ffunction-sections -fdata-sections)

        target_link_options(${target_name} PRIVATE -Wl,--gc-sections)
        target_link_options(${target_name} PRIVATE -Wl,--exclude-libs,ALL)
        target_link_options(${target_name} PRIVATE -flto)
    endif ()

    FetchContent_Declare(ggml SOURCE_DIR "${WHISPER_LIB_DIR}/ggml")
    FetchContent_MakeAvailable(ggml)
    target_compile_options(ggml PRIVATE ${GGML_COMPILE_OPTIONS})
    target_link_libraries(${target_name} ${LOG_LIB} android ggml)

endfunction()

if (${ANDROID_ABI} STREQUAL "arm64-v8a")
    build_library("whisper_v8fp16_va")
elseif (${ANDROID_ABI} STREQUAL "armeabi-v7a")
    build_library("whisper_vfpv4")
endif ()

build_library("whisper") # Default target

include_directories("${WHISPER_LIB_DIR}")
include_directories("${WHISPER_LIB_DIR}/src")
include_directories("${WHISPER_LIB_DIR}/include")
include_directories("${WHISPER_LIB_DIR}/ggml/include")
include_directories("${WHISPER_LIB_DIR}/ggml/src")
include_directories("${WHISPER_LIB_DIR}/ggml/src/ggml-cpu")

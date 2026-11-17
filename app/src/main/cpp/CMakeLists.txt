cmake_minimum_required(VERSION 3.22.1)
project(shbeeb_media LANGUAGES C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# Keep this a small Android-native build: no CLI/server or desktop media codecs.
set(SD_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(SD_BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(SD_BUILD_SHARED_GGML_LIB OFF CACHE BOOL "" FORCE)
set(SD_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(SD_OPENCL OFF CACHE BOOL "" FORCE)
set(SD_WEBP OFF CACHE BOOL "" FORCE)
set(SD_WEBM OFF CACHE BOOL "" FORCE)
set(SD_USE_SYSTEM_GGML OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)

set(SD_CPP_ROOT "${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/stable-diffusion.cpp")
if(NOT EXISTS "${SD_CPP_ROOT}/CMakeLists.txt")
    message(FATAL_ERROR "stable-diffusion.cpp submodule missing. Run git submodule update --init --recursive.")
endif()
if(NOT EXISTS "${SD_CPP_ROOT}/ggml/CMakeLists.txt")
    message(FATAL_ERROR "stable-diffusion.cpp ggml submodule missing. Run git submodule update --init --recursive.")
endif()

add_subdirectory("${SD_CPP_ROOT}" "${CMAKE_CURRENT_BINARY_DIR}/stable-diffusion.cpp")

add_library(shbeeb_media SHARED shbeeb_media_jni.cpp)
target_include_directories(shbeeb_media PRIVATE "${SD_CPP_ROOT}/include")
target_link_libraries(shbeeb_media PRIVATE stable-diffusion android log)
target_link_options(shbeeb_media PRIVATE "-Wl,-z,max-page-size=16384")

cmake_minimum_required(VERSION 3.22.1)
project(fairystockfish_jni)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -Wall -Wextra -DUSE_POPCNT -DNO_PREFETCH")
set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -O3 -DNDEBUG")

# Enable LARGEBOARDS for janggi support
add_definitions(-DLARGEBOARDS)

# 미리 계산된 매직 넘버를 씁니다(magic.h).
# 이게 없으면 Bitboards::init() 이 10x11 판용 매직을 난수로 찾는데, 큰 판에서는
# 사실상 끝나지 않아 엔진이 영영 준비되지 않습니다. ARM 에는 PEXT 가 없어서
# 매직을 우회할 수도 없습니다.
add_definitions(-DPRECOMPUTED_MAGICS)

# Disable NNUE to avoid embedding neural network files
add_definitions(-DNNUE_EMBEDDING_OFF)

# Fairy-Stockfish source collection (excluding main.cpp and Python/JS bindings)
file(GLOB_RECURSE STOCKFISH_SOURCES
    "fairystockfish/src/*.cpp"
)
list(FILTER STOCKFISH_SOURCES EXCLUDE REGEX ".*main\\.cpp$")
list(FILTER STOCKFISH_SOURCES EXCLUDE REGEX ".*ffishjs\\.cpp$")
list(FILTER STOCKFISH_SOURCES EXCLUDE REGEX ".*pyffish\\.cpp$")

# JNI source files (to be created next)
set(JNI_SOURCES
    jni_bridge.cpp
    uci_engine.cpp
)

# Create shared library
add_library(fairystockfish_jni SHARED
    ${STOCKFISH_SOURCES}
    ${JNI_SOURCES}
)

# Include directories
target_include_directories(fairystockfish_jni PRIVATE
    fairystockfish/src
)

# Link libraries
target_link_libraries(fairystockfish_jni
    android
    log
)

# Strip symbols in release builds to reduce binary size
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    add_custom_command(TARGET fairystockfish_jni POST_BUILD
        COMMAND ${CMAKE_STRIP} --strip-unneeded $<TARGET_FILE:fairystockfish_jni>
        COMMENT "Stripping symbols from release binary"
    )
endif()

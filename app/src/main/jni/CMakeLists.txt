# Created by Rikka
cmake_minimum_required(VERSION 3.4.1)

# build executable files to build/starter/<arch>/libblocker.so, which is
# set as jniLibs.srcDirs in build.gradle.
set(EXECUTABLE_OUTPUT_PATH ${CMAKE_SOURCE_DIR}/../../../build/starter/${ANDROID_ABI})

add_executable( libblocker.so
                starter.c )

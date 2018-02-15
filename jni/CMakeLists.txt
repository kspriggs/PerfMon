#Created 2/12/2018
#See - https://developer.android.com/studio/projects/configure-cmake.html
# Sets the minimum version of CMake required to build your native library.
# This ensures that a certain set of CMake features is available to
# your build.

cmake_minimum_required(VERSION 3.4.1)

# Specifies a library name, specifies whether the library is STATIC or
# SHARED, and provides relative paths to the source code. You can
# define multiple libraries by adding multiple add_library() commands,
# and CMake builds them for you. When you build your app, Gradle
# automatically packages shared libraries with your APK.

add_library( # Specifies the name of the library.
             ndk1

             # Sets the library as a shared library.
             SHARED

             # Provides a relative path to your source file(s).
             ../jni/native.c )

find_library( # Sets the name of the path variable.
          log-lib

          # Specifies the name of the NDK library that
          # you want CMake to locate.
          log )


# Links your native library against one or more other native libraries.
target_link_libraries( # Specifies the target library.
                       ndk1

                       # Links the log library to the target library.
                       ${log-lib} )
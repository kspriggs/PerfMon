LOCAL_PATH := $(call my-dir)
 
include $(CLEAR_VARS)

LOCAL_LDLIBS := -llog

LOCAL_MODULE    := ndk1
LOCAL_SRC_FILES := procrank.c

LOCAL_C_INCLUDES := $(call include-path-for, libpagemap)
LOCAL_SHARED_LIBRARIES := libpagemap


include $(BUILD_SHARED_LIBRARY)

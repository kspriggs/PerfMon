#include <jni.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <android/log.h>

#define DEBUG_TAG "NDK_PerfService"

void *ptr[100];
jint bytes_allocated[100];
jint current_allocated = 0;

int Java_com_motorola_tools_perfmon_PerfService_helloLog(JNIEnv * env, jobject this, jstring logThis)
{
    jboolean isCopy;
    const char * szLogThis = (*env)->GetStringUTFChars(env, logThis, &isCopy);

    __android_log_print(ANDROID_LOG_DEBUG, DEBUG_TAG, "NDK:LC: [%s]", szLogThis);

    (*env)->ReleaseStringUTFChars(env, logThis, szLogThis);
	
    return 10;
}

jint Java_com_motorola_tools_perfmon_PerfService_allocateMem(JNIEnv * env, jobject this, jint action, jint size)
{
	jint result = 0;

	if(action == 0 && current_allocated < 100) /* Allocate */
	{
		ptr[current_allocated] = malloc(size);
		if(ptr[current_allocated] != NULL)
		{
			result = size;
			bytes_allocated[current_allocated] = size;
			current_allocated++;
		}
	}
	else if(action == 1 && current_allocated > 0) /* Free */
	{
		jint i;
		for(i=0;i<current_allocated;++i)
 		{
			free(ptr[current_allocated-i]);
			bytes_allocated[current_allocated-i] = 0;
		}
		current_allocated = 0;
	}
    return result;
}

jint Java_com_motorola_tools_perfmon_PerfService_checkMem(JNIEnv * env, jobject this)
{
	jint i;
	jint results = 0;
	for(i=1;i<=current_allocated;++i)
 	{
		if(ptr[current_allocated-i] != NULL)
		{
			results += bytes_allocated[current_allocated-i];
		}
	}
	return results;
}

jobject Java_com_motorola_tools_perfmon_PerfService_allocateByteBuffer(JNIEnv *env, jclass this, jint size)
{
void *p;
if ((p = malloc(size))) {
memset(p, 1, size);
jobject b = (*env)->NewDirectByteBuffer(env,p,size);
return b;
}
else return NULL;
}

void Java_com_motorola_tools_perfmon_PerfService_freeByteBuffer(JNIEnv *env, jclass this, jobject buf)
{
char *p = (*env)->GetDirectBufferAddress(env,buf);
free(p);
}
jint Java_com_motorola_tools_perfmon_PerfService_causePanic(JNIEnv *env, jclass this, jint zero)
{
return 10/zero;
}


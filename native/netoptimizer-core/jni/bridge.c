#include <jni.h>
#include <stdint.h>

/* Rust reads the array only during the call; no JNI reference escapes the thread. */
extern int32_t netopt_tcp_summary(const int32_t *input, uint32_t len, int32_t *output);
extern int32_t netopt_median(const int32_t *input, uint32_t len, uint32_t minimum);

#define MAX_SAMPLES 4096
_Static_assert(sizeof(jint) == sizeof(int32_t), "JNI integers must be 32-bit");

JNIEXPORT jintArray JNICALL
Java_com_doxaiz_netoptimizer_core_net_NativeMeasurements_tcpNative(
        JNIEnv *env, jobject instance, jintArray input) {
    (void)instance;
    if (input == NULL) return NULL;
    jsize len = (*env)->GetArrayLength(env, input);
    if (len < 0 || len > MAX_SAMPLES) return NULL;
    jint *elements = len ? (*env)->GetIntArrayElements(env, input, NULL) : NULL;
    if (len && elements == NULL) return NULL; /* pending JNI exception, if any */
    int32_t result[5];
    int status = netopt_tcp_summary((const int32_t *)elements, (uint32_t)len, result);
    if (elements != NULL) (*env)->ReleaseIntArrayElements(env, input, elements, JNI_ABORT);
    if (status != 0 || (*env)->ExceptionCheck(env)) return NULL;
    jintArray output = (*env)->NewIntArray(env, 5);
    if (output == NULL) return NULL;
    (*env)->SetIntArrayRegion(env, output, 0, 5, (const jint *)result);
    if ((*env)->ExceptionCheck(env)) return NULL;
    return output;
}

JNIEXPORT jint JNICALL
Java_com_doxaiz_netoptimizer_core_net_NativeMeasurements_medianNative(
        JNIEnv *env, jobject instance, jintArray input, jint minimum) {
    (void)instance;
    if (input == NULL || minimum <= 0) return -2;
    jsize len = (*env)->GetArrayLength(env, input);
    if (len < 0 || len > MAX_SAMPLES) return -2;
    jint *elements = len ? (*env)->GetIntArrayElements(env, input, NULL) : NULL;
    if (len && elements == NULL) return -2;
    jint result = netopt_median((const int32_t *)elements, (uint32_t)len, (uint32_t)minimum);
    if (elements != NULL) (*env)->ReleaseIntArrayElements(env, input, elements, JNI_ABORT);
    return result;
}

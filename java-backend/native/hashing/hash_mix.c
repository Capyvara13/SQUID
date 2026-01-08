#include <jni.h>
#include <stdint.h>
#include <string.h>

// JNI interface for com.squid.core.crypto.AssemblyHashMix
// Library name: squid_hashmix
//
// This implementation performs a simple 64-bit word-wise XOR/rotate mix
// over the input buffer. It is designed to be fast and branch-light, and
// will be compiled down to efficient assembly by the C compiler.
//
// Function signatures (matching AssemblyHashMix native declarations):
//   private static native byte[] nativeCustomHashMix(byte[] input, long hardwareFingerprint);
//   private static native long nativeGetHardwareSeed();

static uint64_t rotl64(uint64_t x, int r) {
    return (x << r) | (x >> (64 - r));
}

static uint64_t mix_bytes(const uint8_t *data, size_t len, uint64_t seed) {
    const uint8_t *p = data;
    const uint8_t *end = data + len;
    uint64_t acc = seed ^ (len * 0x9e3779b97f4a7c15ULL);

    while (p + 8 <= end) {
        uint64_t w;
        memcpy(&w, p, 8);
        acc ^= w;
        acc = rotl64(acc, 27) * 0x3c79ac492ba7b653ULL;
        p += 8;
    }
    if (p < end) {
        uint64_t tail = 0;
        size_t remaining = (size_t)(end - p);
        memcpy(&tail, p, remaining);
        acc ^= tail;
        acc = rotl64(acc, 31) * 0x1c69b3f74ac4ae35ULL;
    }

    acc ^= acc >> 33;
    acc *= 0xff51afd7ed558ccdULL;
    acc ^= acc >> 33;
    acc *= 0xc4ceb9fe1a85ec53ULL;
    acc ^= acc >> 33;
    return acc;
}

JNIEXPORT jbyteArray JNICALL Java_com_squid_core_crypto_AssemblyHashMix_nativeCustomHashMix
  (JNIEnv *env, jclass clazz, jbyteArray input, jlong hardwareFingerprint) {
    (void)clazz;

    if (input == NULL) {
        return (*env)->NewByteArray(env, 0);
    }

    jsize len = (*env)->GetArrayLength(env, input);
    jbyte *bytes = (*env)->GetByteArrayElements(env, input, NULL);
    if (bytes == NULL) {
        return NULL; // OutOfMemoryError already thrown
    }

    uint64_t seed = (uint64_t)hardwareFingerprint;
    uint64_t acc = mix_bytes((const uint8_t *)bytes, (size_t)len, seed);

    // Derive a 32-byte output buffer from the accumulator
    uint8_t out[32];
    for (int i = 0; i < 4; ++i) {
        uint64_t v = acc + (uint64_t)i * 0x9e3779b97f4a7c15ULL;
        memcpy(out + i * 8, &v, 8);
    }

    (*env)->ReleaseByteArrayElements(env, input, bytes, JNI_ABORT);

    jbyteArray result = (*env)->NewByteArray(env, 32);
    if (result == NULL) {
        return NULL;
    }
    (*env)->SetByteArrayRegion(env, result, 0, 32, (const jbyte *)out);
    return result;
}

JNIEXPORT jlong JNICALL Java_com_squid_core_crypto_AssemblyHashMix_nativeGetHardwareSeed
  (JNIEnv *env, jclass clazz) {
    (void)env;
    (void)clazz;

    // Simple time-based seed; JVM layer will further combine this with
    // static descriptors if needed.
    uint64_t t = (uint64_t)time(NULL);
    uint64_t seed = mix_bytes((const uint8_t *)&t, sizeof(t), 0x1234567890abcdefULL);
    return (jlong)seed;
}

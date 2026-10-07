#include <jni.h>
#include <string>
#include <fstream>
#include <sstream>
#include <unistd.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <malloc.h>
#include <android/log.h>
#include <cerrno>
#include <dlfcn.h>

#define LOG_TAG "DeepRAM_Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

typedef int (*malloc_trim_t)(size_t);

extern "C" JNIEXPORT jlong JNICALL
Java_com_theblacksheep_appoff_core_NativeMemoryUtils_nGetAvailableMemoryNative(
        JNIEnv* env,
        jobject /* this */) {
    std::ifstream meminfo("/proc/meminfo");
    std::string line;
    jlong memAvailable = -1;
    jlong memFree = 0;
    jlong memCached = 0;
    jlong memBuffers = 0;
    jlong memSReclaimable = 0;

    while (std::getline(meminfo, line)) {
        if (line.compare(0, 13, "MemAvailable:") == 0) {
            std::stringstream ss(line.substr(13));
            ss >> memAvailable;
        } else if (line.compare(0, 8, "MemFree:") == 0) {
            std::stringstream ss(line.substr(8));
            ss >> memFree;
        } else if (line.compare(0, 7, "Cached:") == 0) {
            std::stringstream ss(line.substr(7));
            ss >> memCached;
        } else if (line.compare(0, 8, "Buffers:") == 0) {
            std::stringstream ss(line.substr(8));
            ss >> memBuffers;
        } else if (line.compare(0, 13, "SReclaimable:") == 0) {
            std::stringstream ss(line.substr(13));
            ss >> memSReclaimable;
        }
    }

    if (memAvailable != -1) {
        return memAvailable * 1024;
    }

    return (memFree + memCached + memBuffers + memSReclaimable) * 1024;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_theblacksheep_appoff_core_NativeMemoryUtils_nDropCachesNative(
        JNIEnv* env,
        jobject /* this */,
        jint level) {
    sync();
    int fd = open("/proc/sys/vm/drop_caches", O_WRONLY);
    if (fd == -1) {
        LOGE("Failed to open drop_caches: %d", errno);
        return JNI_FALSE;
    }

    char c = static_cast<char>('0' + (level & 0x7));
    if (write(fd, &c, 1) != 1) {
        LOGE("Failed to write to drop_caches: %d", errno);
        close(fd);
        return JNI_FALSE;
    }

    close(fd);
    LOGI("Kernel caches dropped (level %d)", level);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_theblacksheep_appoff_core_NativeMemoryUtils_nCompactMemoryNative(
        JNIEnv* env,
        jobject /* this */) {
    int fd = open("/proc/sys/vm/compact_memory", O_WRONLY);
    if (fd == -1) {
        LOGE("Failed to open compact_memory: %d", errno);
        return JNI_FALSE;
    }

    if (write(fd, "1", 1) != 1) {
        LOGE("Failed to write to compact_memory: %d", errno);
        close(fd);
        return JNI_FALSE;
    }

    close(fd);
    LOGI("Kernel memory compaction triggered");
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_theblacksheep_appoff_core_NativeMemoryUtils_nTrimMallocNative(
        JNIEnv* env,
        jobject /* this */) {
    // malloc_trim is available in Bionic from API 28. Use dlsym for compatibility with minSdk 26.
    static malloc_trim_t trim_func = nullptr;
    static bool searched = false;

    if (!searched) {
        void* handle = dlopen("libc.so", RTLD_NOW);
        if (handle) {
            trim_func = reinterpret_cast<malloc_trim_t>(dlsym(handle, "malloc_trim"));
        }
        searched = true;
    }

    if (trim_func) {
        trim_func(0);
        LOGI("Native heap trimmed via dynamic linkage");
    } else {
        LOGI("malloc_trim not available on this system");
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_theblacksheep_appoff_core_NativeMemoryUtils_nSetOomScoreAdjNative(
        JNIEnv* env,
        jobject /* this */,
        jint score) {
    char path[64];
    snprintf(path, sizeof(path), "/proc/%d/oom_score_adj", getpid());

    int fd = open(path, O_WRONLY);
    if (fd == -1) {
        LOGE("Failed to open oom_score_adj: %d", errno);
        return JNI_FALSE;
    }

    char buf[16];
    int len = snprintf(buf, sizeof(buf), "%d", score);
    if (write(fd, buf, len) != len) {
        LOGE("Failed to write to oom_score_adj: %d", errno);
        close(fd);
        return JNI_FALSE;
    }

    close(fd);
    LOGI("Self OOM score adjusted to %d", score);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_theblacksheep_appoff_core_NativeMemoryUtils_nGetProcessOomScoreNative(
        JNIEnv* env,
        jobject /* this */,
        jint pid) {
    char path[64];
    snprintf(path, sizeof(path), "/proc/%d/oom_score", pid);

    std::ifstream file(path);
    int score = 0;
    if (file >> score) {
        return score;
    }
    return -1;
}

extern "C" JNIEXPORT void JNICALL
Java_com_theblacksheep_appoff_core_NativeMemoryUtils_nOptimizeMemoryNative(
        JNIEnv* env,
        jobject /* this */) {
    sync();
}

extern "C" JNIEXPORT jint JNICALL
Java_com_theblacksheep_appoff_core_NativeMemoryUtils_nGetSystemFpsNative(
        JNIEnv* env,
        jobject /* this */) {
    const char* paths[] = {
        "/sys/class/graphics/fb0/measured_fps",
        "/sys/class/drm/sde-crtc-0/measured_fps",
        "/sys/devices/platform/soc/ae00000.comm,mdss_mdp/drm/sde-crtc-0/measured_fps"
    };

    for (const char* path : paths) {
        std::ifstream file(path);
        if (file.is_open()) {
            int fps = 0;
            if (file >> fps && fps > 0) {
                return fps;
            }
        }
    }

    return -1;
}

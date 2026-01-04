#include <jni.h>
#include <string>
#include <vector>
#include <fstream>
#include <sstream>
#include <cstring>
#include <dirent.h>
#include <unistd.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <sys/stat.h>
#include <dlfcn.h>
#include <link.h>
#include <android/log.h>
#include <pthread.h>

#define LOG_TAG "RaspDetector"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Frida-related patterns
static const std::vector<std::string> FRIDA_PATTERNS = {
    "frida", "frida-agent", "frida-gadget", "libfrida",
    "re.frida.server", "linjector", "gum-js-loop",
    "pool-frida", "frida-helper", "frida-thread"
};

// Check if string contains any pattern (case-insensitive)
bool containsPattern(const std::string& str, const std::vector<std::string>& patterns) {
    std::string lower = str;
    std::transform(lower.begin(), lower.end(), lower.begin(), ::tolower);
    for (const auto& pattern : patterns) {
        if (lower.find(pattern) != std::string::npos) {
            return true;
        }
    }
    return false;
}

// ==================== MAPS SCANNING ====================
extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_prapps_fridaserverinstaller_rasp_RaspDetector_nativeScanMaps(JNIEnv *env, jobject) {
    std::vector<std::string> results;
    std::ifstream mapsFile("/proc/self/maps");
    
    if (mapsFile.is_open()) {
        std::string line;
        while (std::getline(mapsFile, line)) {
            if (containsPattern(line, FRIDA_PATTERNS)) {
                results.push_back("Suspicious: " + line.substr(0, 80));
            }
            // Check for anonymous executable memory (rwxp)
            if (line.find("rwxp") != std::string::npos && 
                (line.find("[anon:") != std::string::npos || line.length() < 75)) {
                results.push_back("Anon RWX: " + line.substr(0, 60));
            }
        }
    }
    
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray resultArray = env->NewObjectArray(results.size(), stringClass, nullptr);
    for (size_t i = 0; i < results.size(); i++) {
        env->SetObjectArrayElement(resultArray, i, env->NewStringUTF(results[i].c_str()));
    }
    return resultArray;
}

// ==================== SMAPS SCANNING ====================
extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_prapps_fridaserverinstaller_rasp_RaspDetector_nativeScanSmaps(JNIEnv *env, jobject) {
    std::vector<std::string> results;
    std::ifstream smapsFile("/proc/self/smaps");
    
    if (smapsFile.is_open()) {
        std::string line;
        size_t totalAnonExec = 0;
        
        while (std::getline(smapsFile, line)) {
            if (containsPattern(line, FRIDA_PATTERNS)) {
                results.push_back("Frida pattern in smaps");
            }
            // Check header lines for anonymous exec
            if (line.length() > 20 && std::isxdigit(line[0])) {
                if (line.find("rwxp") != std::string::npos) {
                    size_t dashPos = line.find('-');
                    if (dashPos != std::string::npos) {
                        unsigned long start = std::stoul(line.substr(0, dashPos), nullptr, 16);
                        unsigned long end = std::stoul(line.substr(dashPos + 1, 12), nullptr, 16);
                        size_t size = end - start;
                        if (size > 1024 * 1024) { // > 1MB
                            totalAnonExec += size;
                        }
                    }
                }
            }
        }
        
        if (totalAnonExec > 5 * 1024 * 1024) {
            std::ostringstream oss;
            oss << "Large anon exec memory: " << (totalAnonExec / (1024 * 1024)) << "MB";
            results.push_back(oss.str());
        }
    }
    
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray resultArray = env->NewObjectArray(results.size(), stringClass, nullptr);
    for (size_t i = 0; i < results.size(); i++) {
        env->SetObjectArrayElement(resultArray, i, env->NewStringUTF(results[i].c_str()));
    }
    return resultArray;
}

// ==================== FD SCANNING ====================
extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_prapps_fridaserverinstaller_rasp_RaspDetector_nativeScanFds(JNIEnv *env, jobject) {
    std::vector<std::string> results;
    
    DIR *dir = opendir("/proc/self/fd");
    if (dir) {
        struct dirent *entry;
        char linkPath[256], targetPath[512];
        
        while ((entry = readdir(dir)) != nullptr) {
            if (entry->d_name[0] == '.') continue;
            
            snprintf(linkPath, sizeof(linkPath), "/proc/self/fd/%s", entry->d_name);
            ssize_t len = readlink(linkPath, targetPath, sizeof(targetPath) - 1);
            
            if (len > 0) {
                targetPath[len] = '\0';
                if (containsPattern(std::string(targetPath), FRIDA_PATTERNS)) {
                    results.push_back(std::string("FD ") + entry->d_name + ": " + targetPath);
                }
            }
        }
        closedir(dir);
    }
    
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray resultArray = env->NewObjectArray(results.size(), stringClass, nullptr);
    for (size_t i = 0; i < results.size(); i++) {
        env->SetObjectArrayElement(resultArray, i, env->NewStringUTF(results[i].c_str()));
    }
    return resultArray;
}

// ==================== PORT CHECK ====================
extern "C" JNIEXPORT jboolean JNICALL
Java_com_prapps_fridaserverinstaller_rasp_RaspDetector_nativeCheckPort(JNIEnv*, jobject, jint port) {
    int sock = socket(AF_INET, SOCK_STREAM, 0);
    if (sock < 0) return JNI_FALSE;
    
    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_port = htons(port);
    addr.sin_addr.s_addr = inet_addr("127.0.0.1");
    
    // Non-blocking connect with timeout
    int result = connect(sock, (struct sockaddr*)&addr, sizeof(addr));
    close(sock);
    
    return (result == 0) ? JNI_TRUE : JNI_FALSE;
}

// ==================== PTRACE DETECTION ====================
extern "C" JNIEXPORT jboolean JNICALL
Java_com_prapps_fridaserverinstaller_rasp_RaspDetector_nativeCheckPtrace(JNIEnv*, jobject) {
    std::ifstream statusFile("/proc/self/status");
    if (statusFile.is_open()) {
        std::string line;
        while (std::getline(statusFile, line)) {
            if (line.find("TracerPid:") == 0) {
                int tracerPid = std::stoi(line.substr(10));
                return (tracerPid != 0) ? JNI_TRUE : JNI_FALSE;
            }
        }
    }
    return JNI_FALSE;
}

// ==================== THREAD NAME CHECK ====================
extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_prapps_fridaserverinstaller_rasp_RaspDetector_nativeCheckThreads(JNIEnv *env, jobject) {
    std::vector<std::string> results;
    
    std::vector<std::string> suspiciousThreads = {
        "gmain", "gum-js-loop", "pool-frida", "gdbus"
    };
    
    DIR *dir = opendir("/proc/self/task");
    if (dir) {
        struct dirent *entry;
        while ((entry = readdir(dir)) != nullptr) {
            if (entry->d_name[0] == '.') continue;
            
            std::string commPath = "/proc/self/task/" + std::string(entry->d_name) + "/comm";
            std::ifstream commFile(commPath);
            if (commFile.is_open()) {
                std::string threadName;
                std::getline(commFile, threadName);
                
                for (const auto& suspicious : suspiciousThreads) {
                    if (threadName.find(suspicious) != std::string::npos) {
                        results.push_back("Thread: " + threadName);
                        break;
                    }
                }
            }
        }
        closedir(dir);
    }
    
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray resultArray = env->NewObjectArray(results.size(), stringClass, nullptr);
    for (size_t i = 0; i < results.size(); i++) {
        env->SetObjectArrayElement(resultArray, i, env->NewStringUTF(results[i].c_str()));
    }
    return resultArray;
}

// ==================== D-BUS CHECK ====================
extern "C" JNIEXPORT jboolean JNICALL
Java_com_prapps_fridaserverinstaller_rasp_RaspDetector_nativeCheckDbus(JNIEnv*, jobject, jint port) {
    int sock = socket(AF_INET, SOCK_STREAM, 0);
    if (sock < 0) return JNI_FALSE;
    
    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_port = htons(port);
    addr.sin_addr.s_addr = inet_addr("127.0.0.1");
    
    if (connect(sock, (struct sockaddr*)&addr, sizeof(addr)) == 0) {
        // Send D-Bus AUTH message
        const char* authMsg = "\x00AUTH\r\n";
        send(sock, authMsg, strlen(authMsg), 0);
        
        char response[256];
        memset(response, 0, sizeof(response));
        recv(sock, response, sizeof(response) - 1, MSG_DONTWAIT);
        
        close(sock);
        
        // Check for Frida-specific response
        if (strstr(response, "REJECTED") != nullptr || 
            strstr(response, "OK") != nullptr) {
            return JNI_TRUE;
        }
    }
    
    close(sock);
    return JNI_FALSE;
}

// ==================== ENVIRONMENT CHECK ====================
extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_prapps_fridaserverinstaller_rasp_RaspDetector_nativeCheckEnvironment(JNIEnv *env, jobject) {
    std::vector<std::string> results;
    
    std::ifstream environFile("/proc/self/environ");
    if (environFile.is_open()) {
        std::string content((std::istreambuf_iterator<char>(environFile)),
                            std::istreambuf_iterator<char>());
        
        if (containsPattern(content, FRIDA_PATTERNS)) {
            results.push_back("Frida pattern in environment");
        }
        if (content.find("LD_PRELOAD") != std::string::npos) {
            results.push_back("LD_PRELOAD detected");
        }
    }
    
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray resultArray = env->NewObjectArray(results.size(), stringClass, nullptr);
    for (size_t i = 0; i < results.size(); i++) {
        env->SetObjectArrayElement(resultArray, i, env->NewStringUTF(results[i].c_str()));
    }
    return resultArray;
}

// ==================== ADVANCED ENVIRONMENT CHECK ====================

#include "check_env_utils.h"

static std::string check_result = "";
static bool is_env_abnormal = false;

static int dl_iterate_callback(struct dl_phdr_info *info, size_t size, void *data) {
    if (is_linker_sensitive(info->dlpi_name, linker_sensitive_lib)) {
        check_result += "detect sensitive lib from linker: ";
        check_result += info->dlpi_name;
        check_result += "\n";
        is_env_abnormal = true;
        return 1;
    }
    return 0;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_prapps_fridaserverinstaller_rasp_RaspDetector_nativeCheckEnvironmentAbnormal(JNIEnv *env, jobject) {
    check_result = "result: \n";
    is_env_abnormal = false;

    // CRC32 table init if not done (handled in CRC32.h helpers but good to trigger)
    // dl_iterate_phdr check
    dl_iterate_phdr(dl_iterate_callback, NULL);

    // Maps check
    if (is_maps_sensitive(maps_sensitive_words, self_maps)) {
        check_result += "detect suspicious maps\n";
        is_env_abnormal = true;
    }
    if (is_maps_sensitive(maps_sensitive_words, self_smaps)) {
        check_result += "detect suspicious smaps\n";
        is_env_abnormal = true;
    }

    // Anon Exec Memory
    if (has_anon_exec_memory(self_maps)) {
        check_result += "detect maps anonymous executable memory\n";
        is_env_abnormal = true;
    }
    if (has_anon_exec_memory(self_smaps)) {
        check_result += "detect smaps anonymous executable memory\n";
        is_env_abnormal = true;
    }

    // Mem keywords scan
    if (scan_mem_keywords(mem_sensitive_words)) {
        check_result += "detect suspicious mem\n";
        is_env_abnormal = true;
    }

    // Task status scan
    if (scan_task_status(sensitive_task_name)) {
        check_result += "detect suspicious task\n";
        is_env_abnormal = true;
    }

    // Lib integrity
    if (check_all_libs_integrity(crc_solist)) {
        check_result += "detect lib has been hooked\n";
        is_env_abnormal = true;
    }

    if (is_env_abnormal) {
        LOGD(LOG_TAG "%s", check_result.c_str());
    }

    return env->NewStringUTF(check_result.c_str());
}

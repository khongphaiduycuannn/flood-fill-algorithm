#include <jni.h>
#include <android/bitmap.h>
#include <queue>
#include <unordered_set>
#include <unordered_map>
#include <vector>
#include <utility>
#include <atomic>

struct LayerData {
    std::vector<std::pair<int, int>> pixels;
};

struct FloodFillSequence {
    std::vector<LayerData> layers;
    uint32_t fillColor;
    int width;
    int height;
    int currentLayerIndex;
    int totalPixels;
    int filledPixels;

    FloodFillSequence() : currentLayerIndex(0), totalPixels(0), filledPixels(0) {}
};

static std::unordered_map<jlong, FloodFillSequence *> activeSequences;
static std::atomic<jlong> sequenceIdCounter{1};

inline uint32_t argbToRgba(jint argb) {
    auto color = static_cast<uint32_t>(argb);
    uint32_t a = (color >> 24) & 0xFF;
    uint32_t r = (color >> 16) & 0xFF;
    uint32_t g = (color >> 8) & 0xFF;
    uint32_t b = color & 0xFF;
    return (a << 24) | (b << 16) | (g << 8) | r;
}

inline uint32_t colorDistance(uint32_t color1, uint32_t color2) {
    uint32_t r1 = color1 & 0xFF;
    uint32_t g1 = (color1 >> 8) & 0xFF;
    uint32_t b1 = (color1 >> 16) & 0xFF;
    uint32_t a1 = (color1 >> 24) & 0xFF;

    uint32_t r2 = color2 & 0xFF;
    uint32_t g2 = (color2 >> 8) & 0xFF;
    uint32_t b2 = (color2 >> 16) & 0xFF;
    uint32_t a2 = (color2 >> 24) & 0xFF;

    int dr = (int) (r1 - r2);
    int dg = (int) (g1 - g2);
    int db = (int) (b1 - b2);
    int da = (int) (a1 - a2);

    return dr * dr + dg * dg + db * db + da * da;
}


extern "C"
JNIEXPORT jboolean JNICALL
Java_android_project_smooth_floodfill_FloodFillNative_nativeIsColorInvalid(
        JNIEnv *env,
        jobject,
        jint color1,
        jint color2,
        jint tolerance
        ) {
    jint threshold = tolerance * 2;
    threshold = threshold * threshold;
    return colorDistance(color1, color2) > threshold;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_android_project_smooth_floodfill_FloodFillNative_nativeFloodFill(
        JNIEnv *env,
        jobject,
        jobject bitmap,
        jint startX,
        jint startY,
        jint fillColor,
        jint tolerance) {

    AndroidBitmapInfo info;
    void *pixels = nullptr;

    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
        return JNI_FALSE;
    }

    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        return JNI_FALSE;
    }

    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
        return JNI_FALSE;
    }

    int width = info.width;
    int height = info.height;
    auto *pixelData = static_cast<uint32_t *>(pixels);

    uint32_t targetColor = argbToRgba(fillColor);
    uint32_t oldColor = pixelData[startY * width + startX];

    uint32_t threshold = tolerance * 2;
    threshold = threshold * threshold;

    if (colorDistance(oldColor, targetColor) <= threshold) {
        AndroidBitmap_unlockPixels(env, bitmap);
        return JNI_TRUE;
    }

    std::queue<std::pair<int, int>> queue;
    std::unordered_set<int> visited;

    queue.push({startX, startY});
    visited.insert(startY * width + startX);

    int dx[] = {-1, 0, 1, -1, 1, -1, 0, 1};
    int dy[] = {-1, -1, -1, 0, 0, 1, 1, 1};

    while (!queue.empty()) {
        auto [x, y] = queue.front();
        queue.pop();

        int index = y * width + x;

        if (colorDistance(pixelData[index], oldColor) > threshold) {
            continue;
        }

        pixelData[index] = targetColor;

        for (int i = 0; i < 8; i++) {
            int nx = x + dx[i];
            int ny = y + dy[i];

            if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                int nIndex = ny * width + nx;
                if (visited.find(nIndex) == visited.end()) {
                    if (colorDistance(pixelData[nIndex], oldColor) <= threshold) {
                        queue.push({nx, ny});
                        visited.insert(nIndex);
                    }
                }
            }
        }
    }

    AndroidBitmap_unlockPixels(env, bitmap);
    return JNI_TRUE;
}

extern "C"
JNIEXPORT jlong JNICALL
Java_android_project_smooth_floodfill_FloodFillNative_nativePrepareFillSequence(
        JNIEnv *env,
        jobject,
        jobject bitmap,
        jint startX,
        jint startY,
        jint fillColor,
        jint tolerance) {

    AndroidBitmapInfo info;
    void *pixels = nullptr;

    if (AndroidBitmap_getInfo(env, bitmap, &info) < 0) {
        return -1;
    }

    if (info.format != ANDROID_BITMAP_FORMAT_RGBA_8888) {
        return -1;
    }

    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
        return -1;
    }

    int width = info.width;
    int height = info.height;
    auto *pixelData = static_cast<uint32_t *>(pixels);

    uint32_t targetColor = argbToRgba(fillColor);
    uint32_t oldColor = pixelData[startY * width + startX];

    uint32_t threshold = tolerance * 2;
    threshold = threshold * threshold;

    if (colorDistance(oldColor, targetColor) <= threshold) {
        AndroidBitmap_unlockPixels(env, bitmap);
        return -2;
    }

    FloodFillSequence *sequence = new FloodFillSequence();
    sequence->fillColor = targetColor;
    sequence->width = width;
    sequence->height = height;

    std::queue<std::pair<int, int>> queue;
    std::vector<bool> visited(width * height, false);

    queue.push({startX, startY});
    visited[startY * width + startX] = true;

    int dx[] = {-1, 0, 1, -1, 1, -1, 0, 1};
    int dy[] = {-1, -1, -1, 0, 0, 1, 1, 1};

    while (!queue.empty()) {
        int currentLayerSize = queue.size();
        LayerData layer;

        for (int i = 0; i < currentLayerSize; i++) {
            auto [x, y] = queue.front();
            queue.pop();

            int index = y * width + x;

            if (colorDistance(pixelData[index], oldColor) > threshold) {
                continue;
            }

            layer.pixels.push_back({x, y});
            sequence->totalPixels++;

            for (int j = 0; j < 8; j++) {
                int nx = x + dx[j];
                int ny = y + dy[j];

                if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                    int nIndex = ny * width + nx;

                    if (!visited[nIndex]) {
                        if (colorDistance(pixelData[nIndex], oldColor) <= threshold) {
                            queue.push({nx, ny});
                            visited[nIndex] = true;
                        }
                    }
                }
            }
        }

        if (!layer.pixels.empty()) {
            sequence->layers.push_back(layer);
        }
    }

    AndroidBitmap_unlockPixels(env, bitmap);

    jlong sequenceId = sequenceIdCounter.fetch_add(1);
    activeSequences[sequenceId] = sequence;

    return sequenceId;
}

extern "C"
JNIEXPORT jintArray JNICALL
Java_android_project_smooth_floodfill_FloodFillNative_nativeFillNextNLayers(
        JNIEnv *env,
        jobject,
        jobject bitmap,
        jlong sequenceId,
        jint layerCount) {

    auto it = activeSequences.find(sequenceId);
    if (it == activeSequences.end()) {
        return nullptr;
    }

    FloodFillSequence *sequence = it->second;

    void *pixels = nullptr;
    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
        return nullptr;
    }

    auto *pixelData = static_cast<uint32_t *>(pixels);

    int pixelsFilled = 0;
    int layersFilled = 0;
    int startIndex = sequence->currentLayerIndex;
    int endIndex = std::min(startIndex + layerCount, (int) sequence->layers.size());

    for (int i = startIndex; i < endIndex; i++) {
        const LayerData &layer = sequence->layers[i];

        for (const auto &[x, y]: layer.pixels) {
            int index = y * sequence->width + x;
            pixelData[index] = sequence->fillColor;
            pixelsFilled++;
        }

        layersFilled++;
    }

    sequence->currentLayerIndex = endIndex;
    sequence->filledPixels += pixelsFilled;

    bool isComplete = sequence->currentLayerIndex >= sequence->layers.size();
    float progress = sequence->totalPixels > 0 ?
                     (float) sequence->filledPixels / sequence->totalPixels : 1.0f;

    AndroidBitmap_unlockPixels(env, bitmap);

    jintArray result = env->NewIntArray(4);
    jint data[] = {
            (jint) (progress * 10000),
            isComplete ? 1 : 0,
            pixelsFilled,
            (jint) sequence->layers.size()
    };
    env->SetIntArrayRegion(result, 0, 4, data);

    return result;
}

extern "C"
JNIEXPORT void JNICALL
Java_android_project_smooth_floodfill_FloodFillNative_nativeReleaseSequence(
        JNIEnv *env,
        jobject,
        jlong sequenceId) {

    auto it = activeSequences.find(sequenceId);
    if (it != activeSequences.end()) {
        delete it->second;
        activeSequences.erase(it);
    }
}
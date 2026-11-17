package jp.naklab.assu.android.android_myownfont.services;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GeminiFontService {
    private static final String TAG = "GeminiFontService";
    private static final String[] PREFERRED_MODEL_ORDER = {
            "gemini-3.8-flash",
            "gemini-3.8-pro",
            "gemini-3.8-flash-lite",
            "gemini-3.5-flash",
            "gemini-3.5-pro",
            "gemini-3.0-flash",
            "gemini-3.0-pro",
            "gemini-2.5-flash",
            "gemini-2.5-pro",
            "gemini-2.0-flash",
            "gemini-2.0-flash-lite",
            "gemini-2.0-pro",
            "gemini-2.0-flash-exp",
            "gemini-2.0-flash-thinking-exp",
            "gemini-1.5-flash",
            "gemini-1.5-pro",
            "gemini-1.5-flash-8b",
            "gemini-1.5-flash-latest",
            "gemini-1.5-pro-latest",
            "gemini-1.0-pro",
            "gemini-pro"
    };

    private final OkHttpClient httpClient;
    private final Handler mainHandler;

    public static class GlyphData {
        public String character;
        public String glyphName;
        public String unicode;
        public String pathD;
        public int horizAdvX;

        public GlyphData(String character, String glyphName, String unicode, String pathD, int horizAdvX) {
            this.character = character;
            this.glyphName = glyphName;
            this.unicode = unicode;
            this.pathD = pathD;
            this.horizAdvX = horizAdvX;
        }
    }

    public interface ApiValidationListener {
        void onSuccess(List<String> availableModels);
        void onFailure(String error);
    }

    public interface OnFontGenerationListener {
        void onProgress(int percent, String status);
        void onSuccess(String fontFamily, List<GlyphData> glyphs);
        default void onSheetGenerated(Bitmap sheetBitmap, String fontFamily) {}
        void onError(String error);
    }

    private interface ModelDiscoveryCallback {
        void onModelsDiscovered(List<String> models);
    }

    public GeminiFontService() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void validateApiKey(String apiKey, ApiValidationListener listener) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            if (listener != null) listener.onFailure("API key is empty.");
            return;
        }
        final String cleanKey = apiKey.trim();
        String url = "https://generativelanguage.googleapis.com/v1beta/models?key=" + cleanKey;
        Request request = new Request.Builder().url(url).get().build();
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> {
                    if (listener != null) listener.onFailure("Network error: " + e.getMessage());
                });
            }

            @Override
            public void onResponse(Call call, Response response) {
                try {
                    if (!response.isSuccessful()) {
                        String errMsg = "HTTP " + response.code();
                        if (response.code() == 400 || response.code() == 403) {
                            errMsg += ": Invalid API Key or Unauthorized";
                        } else if (response.code() == 404) {
                            errMsg += ": Endpoint not found";
                        }
                        final String finalErr = errMsg;
                        mainHandler.post(() -> {
                            if (listener != null) listener.onFailure(finalErr);
                        });
                        return;
                    }

                    String body = response.body() != null ? response.body().string() : "";
                    JSONObject json = new JSONObject(body);
                    JSONArray modelsArray = json.optJSONArray("models");
                    List<String> discovered = new ArrayList<>();
                    if (modelsArray != null) {
                        for (int i = 0; i < modelsArray.length(); i++) {
                            JSONObject m = modelsArray.getJSONObject(i);
                            String name = m.optString("name", "");
                            if (name.startsWith("models/")) name = name.substring("models/".length());
                            if (name.toLowerCase().contains("gemini")) discovered.add(name);
                        }
                    }
                    if (discovered.isEmpty()) {
                        discovered = getDefaultCandidateModels();
                    }
                    final List<String> prioritized = prioritizeModels(discovered);
                    mainHandler.post(() -> {
                        if (listener != null) listener.onSuccess(prioritized);
                    });
                } catch (Exception e) {
                    mainHandler.post(() -> {
                        if (listener != null) listener.onFailure("Response error: " + e.getMessage());
                    });
                }
            }
        });
    }

    public void generateFontFromPrompt(String apiKey, String stylePrompt, Bitmap referenceBitmap, OnFontGenerationListener listener) {
        generateFontFromPrompt(apiKey, null, stylePrompt, referenceBitmap, listener);
    }

    public void generateFontFromPrompt(String apiKey, String selectedModel, String stylePrompt, Bitmap referenceBitmap, OnFontGenerationListener listener) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            if (listener != null) listener.onError("Gemini API key is required. Please tap Settings in toolbar to enter your key.");
            return;
        }

        final String cleanKey = apiKey.trim();
        postProgress(listener, 5, "Connecting to Gemini service...");

        discoverAvailableModels(cleanKey, models -> {
            if (models == null || models.isEmpty()) {
                models = getDefaultCandidateModels();
            }
            List<String> targetModels = new ArrayList<>(models);
            if (selectedModel != null && !selectedModel.isEmpty()) {
                if ("Random Available".equalsIgnoreCase(selectedModel) || "RANDOM".equalsIgnoreCase(selectedModel)) {
                    java.util.Collections.shuffle(targetModels);
                } else if (!"Auto (Best Available)".equalsIgnoreCase(selectedModel) && !"AUTO".equalsIgnoreCase(selectedModel)) {
                    targetModels.remove(selectedModel);
                    targetModels.add(0, selectedModel);
                }
            }
            executeRequest(targetModels, 0, cleanKey, stylePrompt, referenceBitmap, listener);
        });
    }

    private void discoverAvailableModels(String apiKey, ModelDiscoveryCallback callback) {
        String url = "https://generativelanguage.googleapis.com/v1beta/models?key=" + apiKey;
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.w(TAG, "Failed to list models from Gemini API, using default candidate list", e);
                callback.onModelsDiscovered(getDefaultCandidateModels());
            }

            @Override
            public void onResponse(Call call, Response response) {
                try {
                    if (!response.isSuccessful() || response.body() == null) {
                        Log.w(TAG, "List models returned HTTP " + response.code() + ", using default candidate list");
                        callback.onModelsDiscovered(getDefaultCandidateModels());
                        return;
                    }
                    String body = response.body().string();
                    JSONObject json = new JSONObject(body);
                    JSONArray modelsArray = json.optJSONArray("models");
                    if (modelsArray == null || modelsArray.length() == 0) {
                        callback.onModelsDiscovered(getDefaultCandidateModels());
                        return;
                    }

                    List<String> discovered = new ArrayList<>();
                    for (int i = 0; i < modelsArray.length(); i++) {
                        JSONObject m = modelsArray.getJSONObject(i);
                        String name = m.optString("name", "");
                        if (name.startsWith("models/")) {
                            name = name.substring("models/".length());
                        }
                        JSONArray methods = m.optJSONArray("supportedGenerationMethods");
                        boolean supportsGenerate = false;
                        if (methods != null) {
                            for (int j = 0; j < methods.length(); j++) {
                                if ("generateContent".equalsIgnoreCase(methods.optString(j))) {
                                    supportsGenerate = true;
                                    break;
                                }
                            }
                        }
                        if (supportsGenerate && !name.isEmpty() && name.toLowerCase().contains("gemini")) {
                            discovered.add(name);
                        }
                    }

                    if (discovered.isEmpty()) {
                        callback.onModelsDiscovered(getDefaultCandidateModels());
                        return;
                    }

                    List<String> prioritized = prioritizeModels(discovered);
                    Log.i(TAG, "Discovered " + prioritized.size() + " available Gemini models (up to 3.8): " + prioritized);
                    callback.onModelsDiscovered(prioritized);

                } catch (Exception e) {
                    Log.w(TAG, "Error parsing models list, using default candidate list", e);
                    callback.onModelsDiscovered(getDefaultCandidateModels());
                }
            }
        });
    }

    private List<String> prioritizeModels(List<String> discovered) {
        List<String> ordered = new ArrayList<>(discovered);
        java.util.Collections.sort(ordered, (a, b) -> {
            int scoreA = calculateModelScore(a);
            int scoreB = calculateModelScore(b);
            if (scoreA != scoreB) {
                return Integer.compare(scoreB, scoreA); // descending
            }
            return a.compareToIgnoreCase(b);
        });
        return ordered;
    }

    private int calculateModelScore(String name) {
        if (name == null) return 0;
        String lower = name.toLowerCase();

        int major = 0;
        int minor = 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)\\.(\\d+)").matcher(lower);
        if (m.find()) {
            try {
                major = Integer.parseInt(m.group(1));
                minor = Integer.parseInt(m.group(2));
            } catch (Exception ignored) {}
        } else if (lower.contains("pro") && !lower.contains("1.")) {
            major = 1;
            minor = 0;
        }

        int score = major * 1000 + minor * 100;

        if (lower.contains("flash") && !lower.contains("8b") && !lower.contains("lite")) {
            score += 50;
        } else if (lower.contains("flash-lite")) {
            score += 40;
        } else if (lower.contains("pro")) {
            score += 30;
        } else if (lower.contains("8b")) {
            score += 20;
        } else if (lower.contains("exp")) {
            score += 10;
        }

        for (int i = 0; i < PREFERRED_MODEL_ORDER.length; i++) {
            if (PREFERRED_MODEL_ORDER[i].equalsIgnoreCase(lower)) {
                score += (PREFERRED_MODEL_ORDER.length - i);
                break;
            }
        }

        return score;
    }

    private List<String> getDefaultCandidateModels() {
        List<String> list = new ArrayList<>();
        for (String m : PREFERRED_MODEL_ORDER) {
            list.add(m);
        }
        return list;
    }

    private void executeRequest(List<String> modelsToTry, int modelIdx, String apiKey, String stylePrompt, Bitmap referenceBitmap, OnFontGenerationListener listener) {
        if (modelsToTry == null || modelsToTry.isEmpty()) {
            modelsToTry = getDefaultCandidateModels();
        }
        final List<String> currentModels = modelsToTry;
        final String modelName = (modelIdx >= 0 && modelIdx < currentModels.size()) ? currentModels.get(modelIdx) : "gemini-1.5-flash";
        int progressBase = 10 + Math.min(modelIdx * 8, 30);
        postProgress(listener, progressBase, "Connecting to Gemini (" + modelName + ")...");

        try {
            JSONObject rootJson = new JSONObject();
            JSONArray contentsArray = new JSONArray();
            JSONObject userContent = new JSONObject();
            userContent.put("role", "user");

            JSONArray partsArray = new JSONArray();

            if (referenceBitmap != null) {
                postProgress(listener, progressBase + 10, "Encoding style reference image...");
                JSONObject inlineData = new JSONObject();
                inlineData.put("mimeType", "image/jpeg");
                inlineData.put("data", bitmapToBase64(referenceBitmap));

                JSONObject imagePart = new JSONObject();
                imagePart.put("inlineData", inlineData);
                partsArray.put(imagePart);
            }

            // Default hidden prompt: 16:9 large format solid white background with jet black characters across 4-5 rows
            String fullPrompt = "You are an expert typography and vector font designer.\n" +
                    "TASK: Generate a complete font character set in the selected style: " +
                    (stylePrompt != null && !stylePrompt.isEmpty() ? "'" + stylePrompt + "'" : "'Modern Clean Sans-Serif'") + ".\n" +
                    (referenceBitmap != null ? "Analyze the typography style of the provided letter image and replicate its exact visual characteristics across all glyphs.\n" : "") +
                    "\nCOMPOSITION & SPECIFICATION REQUIREMENTS:\n" +
                    "- Solid pure white background (#FFFFFF) in 16:9 large format.\n" +
                    "- All characters must be solid, crisp, jet-black (#000000) only — strictly NO other colors, NO gradients, NO 3D shading, NO decorative borders, and NO background artwork.\n" +
                    "- Maintain distinct spacing and clear gaps between adjacent letters (> 40px gap) in 4-5 neat horizontal rows so each character can be cleanly isolated and contour-traced letter by letter with exact gap boundaries.\n" +
                    "- Include all character types arranged across 4-5 neat horizontal rows:\n" +
                    "  * Row 1: Uppercase letters A-Z\n" +
                    "  * Row 2: Lowercase letters a-z\n" +
                    "  * Row 3: Digits 0-9\n" +
                    "  * Row 4 & 5: Common punctuation and symbols (!@#$%^&*()_+-=[]{}|;:,.<>?/)\n" +
                    "- Standard font coordinate units: units-per-em = 1000, baseline y = 0, cap-height y = 750 to 800.\n" +
                    "- Positive Y points UPWARDS from the baseline.\n" +
                    "- Each glyph's 'd' attribute must be a clean, valid closed SVG vector path (using M, L, Q, C, Z commands) corresponding exactly to the solid black character shape on the white sheet.\n" +
                    "- Set an accurate horizAdvX for each character (typically 650 to 900).\n\n" +
                    "Return ONLY a valid JSON object matching this schema:\n" +
                    "{\n" +
                    "  \"fontFamily\": \"StyledFont\",\n" +
                    "  \"glyphs\": [\n" +
                    "    {\n" +
                    "      \"char\": \"A\",\n" +
                    "      \"glyphName\": \"uni41\",\n" +
                    "      \"unicode\": \"&#x41;\",\n" +
                    "      \"d\": \"M... Z\",\n" +
                    "      \"horizAdvX\": 800\n" +
                    "    }\n" +
                    "  ]\n" +
                    "}";

            JSONObject textPart = new JSONObject();
            textPart.put("text", fullPrompt);
            partsArray.put(textPart);

            userContent.put("parts", partsArray);
            contentsArray.put(userContent);
            rootJson.put("contents", contentsArray);

            JSONObject generationConfig = new JSONObject();
            if (!modelName.contains("1.0") && !modelName.equalsIgnoreCase("gemini-pro")) {
                generationConfig.put("responseMimeType", "application/json");
            }
            generationConfig.put("temperature", 0.3);
            generationConfig.put("maxOutputTokens", 8192);
            rootJson.put("generationConfig", generationConfig);

            String requestUrl = "https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent?key=" + apiKey;
            RequestBody requestBody = RequestBody.create(
                    MediaType.parse("application/json; charset=utf-8"),
                    rootJson.toString()
            );

            Request request = new Request.Builder()
                    .url(requestUrl)
                    .post(requestBody)
                    .build();

            final int estimatedTotalSeconds = 16;
            final long startTime = System.currentTimeMillis();
            final int startPercent = 40;
            final int maxWaitPercent = 88;
            final boolean[] isCompleted = { false };

            final Runnable progressTicker = new Runnable() {
                @Override
                public void run() {
                    if (isCompleted[0]) return;
                    long elapsedMillis = System.currentTimeMillis() - startTime;
                    int elapsedSeconds = (int) (elapsedMillis / 1000);
                    int remainingSeconds = Math.max(1, estimatedTotalSeconds - elapsedSeconds);

                    float progressFraction = (float) elapsedSeconds / (float) estimatedTotalSeconds;
                    int currentPercent = startPercent + (int) ((maxWaitPercent - startPercent) * Math.min(progressFraction, 0.98f));
                    currentPercent = Math.min(maxWaitPercent, Math.max(startPercent, currentPercent));

                    String etaStatus = "Generating Typography • ETA: " + remainingSeconds + "s";
                    postProgress(listener, currentPercent, etaStatus);

                    if (!isCompleted[0]) {
                        mainHandler.postDelayed(this, 1000);
                    }
                }
            };

            postProgress(listener, startPercent, "Generating Typography • ETA: " + estimatedTotalSeconds + "s");
            mainHandler.postDelayed(progressTicker, 1000);

            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    isCompleted[0] = true;
                    mainHandler.removeCallbacks(progressTicker);
                    Log.w(TAG, "Request failed for model " + modelName, e);
                    if (modelIdx + 1 < currentModels.size()) {
                        String nextModel = currentModels.get(modelIdx + 1);
                        postProgress(listener, 42, "Generating Typography (Retrying)...");
                        executeRequest(currentModels, modelIdx + 1, apiKey, stylePrompt, referenceBitmap, listener);
                    } else {
                        postError(listener, "Network error: Unable to reach Gemini server (" + e.getMessage() + ")");
                    }
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    isCompleted[0] = true;
                    mainHandler.removeCallbacks(progressTicker);
                    int code = response.code();
                    String responseBody = (response.body() != null) ? response.body().string() : "";

                    // Extract detailed error message from Google's response if available
                    String googleErrorMsg = "";
                    if (!responseBody.isEmpty()) {
                        try {
                            JSONObject errJson = new JSONObject(responseBody);
                            if (errJson.has("error")) {
                                JSONObject errObj = errJson.getJSONObject("error");
                                googleErrorMsg = errObj.optString("message", "");
                            }
                        } catch (Exception ignored) {}
                    }

                    // Check if request failed (404 Not Found, 400 Bad Request/unsupported, 429 Rate Limit, 5xx server error)
                    if (!response.isSuccessful()) {
                        if (modelIdx + 1 < currentModels.size()) {
                            String nextModel = currentModels.get(modelIdx + 1);
                            Log.w(TAG, modelName + " returned HTTP " + code + " (" + googleErrorMsg + "). Retrying with " + nextModel);
                            postProgress(listener, 42, "Generating Typography (Retrying)...");
                            executeRequest(currentModels, modelIdx + 1, apiKey, stylePrompt, referenceBitmap, listener);
                            return;
                        } else {
                            String msg = googleErrorMsg.isEmpty()
                                    ? ("Gemini API error (HTTP " + code + "). All " + currentModels.size() + " models tried.")
                                    : ("Gemini (" + code + "): " + googleErrorMsg);
                            postError(listener, msg);
                            return;
                        }
                    }

                    try {
                        postProgress(listener, 89, "Generating Typography • Parsing...");
                        JSONObject resObj = new JSONObject(responseBody);
                        JSONArray candidates = resObj.optJSONArray("candidates");
                        if (candidates == null || candidates.length() == 0) {
                            if (modelIdx + 1 < currentModels.size()) {
                                String nextModel = currentModels.get(modelIdx + 1);
                                postProgress(listener, 42, "Generating Typography (Retrying)...");
                                executeRequest(currentModels, modelIdx + 1, apiKey, stylePrompt, referenceBitmap, listener);
                                return;
                            }
                            postError(listener, "No response generated by Gemini.");
                            return;
                        }

                        JSONObject candidate = candidates.getJSONObject(0);
                        JSONObject content = candidate.optJSONObject("content");
                        JSONArray parts = content != null ? content.optJSONArray("parts") : null;
                        if (parts == null || parts.length() == 0) {
                            if (modelIdx + 1 < currentModels.size()) {
                                String nextModel = currentModels.get(modelIdx + 1);
                                postProgress(listener, 42, "Generating Typography (Retrying)...");
                                executeRequest(currentModels, modelIdx + 1, apiKey, stylePrompt, referenceBitmap, listener);
                                return;
                            }
                            postError(listener, "Empty response from Gemini.");
                            return;
                        }

                        // Check if an image sheet was returned as inlineData
                        for (int p = 0; p < parts.length(); p++) {
                            JSONObject partObj = parts.getJSONObject(p);
                            if (partObj.has("inlineData")) {
                                JSONObject inlineObj = partObj.getJSONObject("inlineData");
                                String base64Img = inlineObj.getString("data");
                                byte[] decoded = Base64.decode(base64Img, Base64.DEFAULT);
                                Bitmap sheetBmp = BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
                                if (sheetBmp != null) {
                                    postProgress(listener, 92, "Generating Typography • Tracing...");
                                    mainHandler.post(() -> {
                                        if (listener != null) listener.onSheetGenerated(sheetBmp, "GeminiFontSheet");
                                    });
                                    return;
                                }
                            }
                        }

                        // Otherwise parse vector JSON
                        String rawJson = parts.getJSONObject(0).getString("text");
                        rawJson = rawJson.trim();
                        if (rawJson.startsWith("```json")) {
                            rawJson = rawJson.substring(7);
                        } else if (rawJson.startsWith("```")) {
                            rawJson = rawJson.substring(3);
                        }
                        if (rawJson.endsWith("```")) {
                            rawJson = rawJson.substring(0, rawJson.length() - 3);
                        }
                        rawJson = rawJson.trim();

                        int startBrace = rawJson.indexOf('{');
                        int endBrace = rawJson.lastIndexOf('}');
                        if (startBrace >= 0 && endBrace > startBrace) {
                            rawJson = rawJson.substring(startBrace, endBrace + 1);
                        }

                        JSONObject fontObj = new JSONObject(rawJson);
                        String fontFamily = fontObj.optString("fontFamily", "AIGeneratedFont");
                        JSONArray glyphsArray = fontObj.optJSONArray("glyphs");

                        List<GlyphData> glyphList = new ArrayList<>();
                        if (glyphsArray != null) {
                            for (int i = 0; i < glyphsArray.length(); i++) {
                                JSONObject g = glyphsArray.getJSONObject(i);
                                String ch = g.optString("char", "");
                                String glyphName = g.optString("glyphName", "");
                                String unicode = g.optString("unicode", "");
                                String d = g.optString("d", "");
                                int advX = g.optInt("horizAdvX", 750);

                                if (!d.isEmpty()) {
                                    glyphList.add(new GlyphData(ch, glyphName, unicode, d, advX));
                                }
                            }
                        }

                        postProgress(listener, 100, "Typography Ready!");
                        mainHandler.post(() -> {
                            if (listener != null) {
                                listener.onSuccess(fontFamily, glyphList);
                            }
                        });

                    } catch (Exception ex) {
                        Log.e(TAG, "Error parsing Gemini response from " + modelName, ex);
                        if (modelIdx + 1 < currentModels.size()) {
                            String nextModel = currentModels.get(modelIdx + 1);
                            postProgress(listener, 42, "Generating Typography (Retrying)...");
                            executeRequest(currentModels, modelIdx + 1, apiKey, stylePrompt, referenceBitmap, listener);
                            return;
                        }
                        postError(listener, "Could not parse font glyphs: " + ex.getMessage());
                    }
                }
            });

        } catch (Exception ex) {
            Log.e(TAG, "Failed to build Gemini request for " + modelName, ex);
            if (modelIdx + 1 < currentModels.size()) {
                executeRequest(currentModels, modelIdx + 1, apiKey, stylePrompt, referenceBitmap, listener);
            } else {
                if (listener != null) listener.onError("Request error: " + ex.getMessage());
            }
        }
    }

    private void postProgress(OnFontGenerationListener listener, int percent, String message) {
        mainHandler.post(() -> {
            if (listener != null) listener.onProgress(percent, message);
        });
    }

    private void postError(OnFontGenerationListener listener, String message) {
        mainHandler.post(() -> {
            if (listener != null) listener.onError(message);
        });
    }

    private String bitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, byteArrayOutputStream);
        byte[] byteArray = byteArrayOutputStream.toByteArray();
        return Base64.encodeToString(byteArray, Base64.NO_WRAP);
    }
}

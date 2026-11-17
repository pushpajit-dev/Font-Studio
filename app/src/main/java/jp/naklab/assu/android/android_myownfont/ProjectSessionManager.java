package jp.naklab.assu.android.android_myownfont;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ProjectSessionManager {
    private static final String TAG = "ProjectSessionManager";
    private static final String SESSIONS_DIR = "sessions";

    public static class ProjectSession {
        public String projectName;
        public String fontName;
        public String languageScriptId;
        public long lastModified;
        public int glyphCount;
        public List<SavedGlyph> glyphs = new ArrayList<>();

        public static class SavedGlyph {
            public String glyphName;
            public String unicode;
            public String pathD;
            public int horizAdvX;
        }
    }

    private static File getSessionsDirectory(Context context) {
        File dir = new File(context.getFilesDir(), SESSIONS_DIR);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static boolean saveSession(Context context, String projectName, String fontName, String languageScriptId, Map<String, String> customGlyphsMap) {
        try {
            JSONObject root = new JSONObject();
            root.put("projectName", projectName);
            root.put("fontName", fontName);
            root.put("languageScriptId", languageScriptId);
            root.put("lastModified", System.currentTimeMillis());

            JSONArray glyphsArray = new JSONArray();
            if (customGlyphsMap != null) {
                for (Map.Entry<String, String> entry : customGlyphsMap.entrySet()) {
                    String raw = entry.getValue();
                    JSONObject g = new JSONObject();
                    g.put("glyphName", entry.getKey());
                    g.put("rawXml", raw);
                    glyphsArray.put(g);
                }
            }
            root.put("glyphs", glyphsArray);

            File file = new File(getSessionsDirectory(context), projectName + ".json");
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(root.toString(2).getBytes(StandardCharsets.UTF_8));
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error saving session", e);
            return false;
        }
    }

    public static List<ProjectSession> listSessions(Context context) {
        List<ProjectSession> list = new ArrayList<>();
        File dir = getSessionsDirectory(context);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) return list;

        for (File f : files) {
            try (FileInputStream fis = new FileInputStream(f)) {
                byte[] data = new byte[(int) f.length()];
                fis.read(data);
                String jsonStr = new String(data, StandardCharsets.UTF_8);
                JSONObject root = new JSONObject(jsonStr);

                ProjectSession session = new ProjectSession();
                session.projectName = root.optString("projectName", f.getName().replace(".json", ""));
                session.fontName = root.optString("fontName", session.projectName);
                session.languageScriptId = root.optString("languageScriptId", "latin");
                session.lastModified = root.optLong("lastModified", f.lastModified());
                JSONArray arr = root.optJSONArray("glyphs");
                session.glyphCount = arr != null ? arr.length() : 0;
                list.add(session);
            } catch (Exception e) {
                Log.w(TAG, "Error reading session " + f.getName(), e);
            }
        }
        return list;
    }

    public static boolean loadSession(Context context, String projectName, FontMaker fontMaker) {
        try {
            File file = new File(getSessionsDirectory(context), projectName + ".json");
            if (!file.exists()) return false;

            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] data = new byte[(int) file.length()];
                fis.read(data);
                JSONObject root = new JSONObject(new String(data, StandardCharsets.UTF_8));
                JSONArray arr = root.optJSONArray("glyphs");

                fontMaker.clearCustomGlyphs();
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject g = arr.getJSONObject(i);
                        String name = g.getString("glyphName");
                        String raw = g.getString("rawXml");
                        fontMaker.customGlyphsMap.put(name, raw);
                    }
                }
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading session", e);
            return false;
        }
    }
}

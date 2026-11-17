package jp.naklab.assu.android.android_myownfont;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Handles batch importing of per-letter character images:
 * 1. Multiple image Uris selected directly from file/gallery picker.
 * 2. ZIP archives containing individual letter images (unzipped and parsed).
 *
 * Automatically matches filenames to characters (e.g. A.png -> 'A', uni41.png -> 'A', etc.)
 * or assigns sequentially in Latin alphabetical order.
 * Traces each image 100% offline using OpenCV vectorization.
 */
public class BatchImageFontImporter {

    private static final String TAG = "BatchImageImporter";

    public interface OnBatchImportListener {
        void onProgress(int current, int total, char character, String status);
        void onComplete(int successfulCount, int totalCount);
        void onError(String error);
    }

    public static class ImageEntry {
        public final String name;
        public final Uri uri;
        public final File file;

        public ImageEntry(String name, Uri uri) {
            this.name = name;
            this.uri = uri;
            this.file = null;
        }

        public ImageEntry(String name, File file) {
            this.name = name;
            this.uri = null;
            this.file = file;
        }
    }

    /**
     * Imports multiple image Uris selected by user.
     */
    public static void importMultipleImageUris(
            Context context,
            List<Uri> uris,
            List<MainActivity.CharacterItem> characterList,
            FontSheetDetector fontSheetDetector,
            OpenCVEdgeDetector openCVEdgeDetector,
            FontMaker fontMaker,
            OnBatchImportListener listener) {

        new Thread(() -> {
            Handler mainHandler = new Handler(Looper.getMainLooper());
            try {
                if (uris == null || uris.isEmpty()) {
                    postError(mainHandler, listener, "No images selected.");
                    return;
                }

                List<ImageEntry> entries = new ArrayList<>();
                for (Uri u : uris) {
                    String name = getFileName(context, u);
                    entries.add(new ImageEntry(name, u));
                }

                processImageEntries(context, entries, characterList, fontSheetDetector, openCVEdgeDetector, fontMaker, listener, mainHandler);

            } catch (Exception e) {
                Log.e(TAG, "Batch import error", e);
                postError(mainHandler, listener, "Import failed: " + e.getMessage());
            }
        }).start();
    }

    /**
     * Extracts a ZIP archive and imports all contained character images.
     */
    public static void importZipArchive(
            Context context,
            Uri zipUri,
            List<MainActivity.CharacterItem> characterList,
            FontSheetDetector fontSheetDetector,
            OpenCVEdgeDetector openCVEdgeDetector,
            FontMaker fontMaker,
            OnBatchImportListener listener) {

        new Thread(() -> {
            Handler mainHandler = new Handler(Looper.getMainLooper());
            File tempExtractDir = null;
            try {
                postProgress(mainHandler, listener, 0, 100, ' ', "Extracting ZIP archive...");

                tempExtractDir = new File(context.getCacheDir(), "unzipped_font_" + System.currentTimeMillis());
                if (!tempExtractDir.mkdirs()) {
                    Log.w(TAG, "Failed to create extract dir, continuing if exists");
                }

                List<File> imageFiles = unzipArchive(context, zipUri, tempExtractDir);
                if (imageFiles.isEmpty()) {
                    postError(mainHandler, listener, "No valid PNG/JPG image files found inside ZIP archive.");
                    return;
                }

                // Sort files naturally by filename
                Collections.sort(imageFiles, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));

                List<ImageEntry> entries = new ArrayList<>();
                for (File f : imageFiles) {
                    entries.add(new ImageEntry(f.getName(), f));
                }

                processImageEntries(context, entries, characterList, fontSheetDetector, openCVEdgeDetector, fontMaker, listener, mainHandler);

            } catch (Exception e) {
                Log.e(TAG, "ZIP import error", e);
                postError(mainHandler, listener, "ZIP extraction failed: " + e.getMessage());
            } finally {
                // Cleanup temp unzipped files
                if (tempExtractDir != null && tempExtractDir.exists()) {
                    deleteRecursive(tempExtractDir);
                }
            }
        }).start();
    }

    private static void processImageEntries(
            Context context,
            List<ImageEntry> entries,
            List<MainActivity.CharacterItem> characterList,
            FontSheetDetector fontSheetDetector,
            OpenCVEdgeDetector openCVEdgeDetector,
            FontMaker fontMaker,
            OnBatchImportListener listener,
            Handler mainHandler) {

        int total = entries.size();
        int successCount = 0;

        for (int i = 0; i < total; i++) {
            ImageEntry entry = entries.get(i);

            // Match character
            MainActivity.CharacterItem targetItem = matchCharacterItem(entry.name, characterList, i);
            if (targetItem != null && characterList != null) {
                boolean inList = false;
                for (MainActivity.CharacterItem item : characterList) {
                    if (item.unicode == targetItem.unicode) {
                        inList = true;
                        targetItem = item;
                        break;
                    }
                }
                if (!inList) {
                    final MainActivity.CharacterItem newItem = targetItem;
                    mainHandler.post(() -> characterList.add(newItem));
                }
            }
            char displayChar = targetItem != null ? targetItem.character : '?';

            postProgress(mainHandler, listener, i + 1, total, displayChar,
                    "Tracing (" + (i + 1) + "/" + total + "): '" + displayChar + "'");

            Bitmap bmp = loadImageBitmap(context, entry);
            if (bmp != null && targetItem != null) {
                String pathD = fontSheetDetector.vectorizeCroppedBitmap(bmp);
                if (pathD.isEmpty() && openCVEdgeDetector != null) {
                    pathD = openCVEdgeDetector.extractPathDFromBitmap(bmp);
                }

                if (!pathD.isEmpty()) {
                    String unicode = "&#x" + Integer.toHexString(targetItem.unicode) + ";";
                    fontMaker.addCustomGlyph(targetItem.uId, unicode, pathD, 800);
                    targetItem.isGenerated = true;
                    successCount++;
                }

                bmp.recycle();
            }
        }

        final int finalSuccess = successCount;
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onComplete(finalSuccess, total);
            }
        });
    }

    private static final java.util.Map<String, Character> BENGALI_TRANSLITERATION = new java.util.HashMap<>();
    static {
        // Vowels
        BENGALI_TRANSLITERATION.put("a", 'অ');
        BENGALI_TRANSLITERATION.put("o", 'অ');
        BENGALI_TRANSLITERATION.put("aa", 'আ');
        BENGALI_TRANSLITERATION.put("i", 'ই');
        BENGALI_TRANSLITERATION.put("ii", 'ঈ');
        BENGALI_TRANSLITERATION.put("ee", 'ঈ');
        BENGALI_TRANSLITERATION.put("u", 'উ');
        BENGALI_TRANSLITERATION.put("uu", 'ঊ');
        BENGALI_TRANSLITERATION.put("oo", 'ঊ');
        BENGALI_TRANSLITERATION.put("ri", 'ঋ');
        BENGALI_TRANSLITERATION.put("e", 'এ');
        BENGALI_TRANSLITERATION.put("oi", 'ঐ');
        BENGALI_TRANSLITERATION.put("ai", 'ঐ');
        BENGALI_TRANSLITERATION.put("ou", 'ঔ');
        BENGALI_TRANSLITERATION.put("au", 'ঔ');

        // Consonants (including Gha 'ঘ' specifically)
        BENGALI_TRANSLITERATION.put("k", 'ক');
        BENGALI_TRANSLITERATION.put("ka", 'ক');
        BENGALI_TRANSLITERATION.put("kh", 'খ');
        BENGALI_TRANSLITERATION.put("kha", 'খ');
        BENGALI_TRANSLITERATION.put("g", 'গ');
        BENGALI_TRANSLITERATION.put("ga", 'গ');
        BENGALI_TRANSLITERATION.put("gh", 'ঘ');
        BENGALI_TRANSLITERATION.put("gha", 'ঘ');
        BENGALI_TRANSLITERATION.put("ng", 'ঙ');
        BENGALI_TRANSLITERATION.put("nga", 'ঙ');
        BENGALI_TRANSLITERATION.put("uma", 'ঙ');
        BENGALI_TRANSLITERATION.put("ch", 'চ');
        BENGALI_TRANSLITERATION.put("cha", 'চ');
        BENGALI_TRANSLITERATION.put("ca", 'চ');
        BENGALI_TRANSLITERATION.put("chh", 'ছ');
        BENGALI_TRANSLITERATION.put("chha", 'ছ');
        BENGALI_TRANSLITERATION.put("j", 'জ');
        BENGALI_TRANSLITERATION.put("ja", 'জ');
        BENGALI_TRANSLITERATION.put("jh", 'ঝ');
        BENGALI_TRANSLITERATION.put("jha", 'ঝ');
        BENGALI_TRANSLITERATION.put("ny", 'ঞ');
        BENGALI_TRANSLITERATION.put("nya", 'ঞ');
        BENGALI_TRANSLITERATION.put("ina", 'ঞ');
        BENGALI_TRANSLITERATION.put("tt", 'ট');
        BENGALI_TRANSLITERATION.put("tta", 'ট');
        BENGALI_TRANSLITERATION.put("tth", 'ঠ');
        BENGALI_TRANSLITERATION.put("ttha", 'ঠ');
        BENGALI_TRANSLITERATION.put("dd", 'ড');
        BENGALI_TRANSLITERATION.put("dda", 'ড');
        BENGALI_TRANSLITERATION.put("ddh", 'ঢ');
        BENGALI_TRANSLITERATION.put("ddha", 'ঢ');
        BENGALI_TRANSLITERATION.put("nn", 'ণ');
        BENGALI_TRANSLITERATION.put("nna", 'ণ');
        BENGALI_TRANSLITERATION.put("t", 'ত');
        BENGALI_TRANSLITERATION.put("ta", 'ত');
        BENGALI_TRANSLITERATION.put("th", 'থ');
        BENGALI_TRANSLITERATION.put("tha", 'থ');
        BENGALI_TRANSLITERATION.put("d", 'দ');
        BENGALI_TRANSLITERATION.put("da", 'দ');
        BENGALI_TRANSLITERATION.put("dh", 'ধ');
        BENGALI_TRANSLITERATION.put("dha", 'ধ');
        BENGALI_TRANSLITERATION.put("n", 'ন');
        BENGALI_TRANSLITERATION.put("na", 'ন');
        BENGALI_TRANSLITERATION.put("p", 'প');
        BENGALI_TRANSLITERATION.put("pa", 'প');
        BENGALI_TRANSLITERATION.put("ph", 'ফ');
        BENGALI_TRANSLITERATION.put("pha", 'ফ');
        BENGALI_TRANSLITERATION.put("fa", 'ফ');
        BENGALI_TRANSLITERATION.put("b", 'ব');
        BENGALI_TRANSLITERATION.put("ba", 'ব');
        BENGALI_TRANSLITERATION.put("bh", 'ভ');
        BENGALI_TRANSLITERATION.put("bha", 'ভ');
        BENGALI_TRANSLITERATION.put("va", 'ভ');
        BENGALI_TRANSLITERATION.put("m", 'ম');
        BENGALI_TRANSLITERATION.put("ma", 'ম');
        BENGALI_TRANSLITERATION.put("y", 'য');
        BENGALI_TRANSLITERATION.put("ya", 'য');
        BENGALI_TRANSLITERATION.put("r", 'র');
        BENGALI_TRANSLITERATION.put("ra", 'র');
        BENGALI_TRANSLITERATION.put("l", 'ল');
        BENGALI_TRANSLITERATION.put("la", 'ল');
        BENGALI_TRANSLITERATION.put("sh", 'শ');
        BENGALI_TRANSLITERATION.put("sha", 'শ');
        BENGALI_TRANSLITERATION.put("ss", 'ষ');
        BENGALI_TRANSLITERATION.put("ssa", 'ষ');
        BENGALI_TRANSLITERATION.put("shh", 'ষ');
        BENGALI_TRANSLITERATION.put("shha", 'ষ');
        BENGALI_TRANSLITERATION.put("s", 'স');
        BENGALI_TRANSLITERATION.put("sa", 'স');
        BENGALI_TRANSLITERATION.put("h", 'হ');
        BENGALI_TRANSLITERATION.put("ha", 'হ');
        BENGALI_TRANSLITERATION.put("rr", '\u09DC');
        BENGALI_TRANSLITERATION.put("rra", '\u09DC');
        BENGALI_TRANSLITERATION.put("rrh", '\u09DD');
        BENGALI_TRANSLITERATION.put("rrha", '\u09DD');
        BENGALI_TRANSLITERATION.put("yy", '\u09DF');
        BENGALI_TRANSLITERATION.put("yya", '\u09DF');
    }

    /**
     * Intelligent character matcher:
     * 1. Exact native Unicode characters (e.g. "ঘ.png", "ক.png")
     * 2. Bengali phonetic/transliteration match (e.g. "gha.png", "04_Gha.png", "ka.png")
     * 3. Exact Latin letter match: "A.png" -> 'A', "a.png" -> 'a'
     * 4. Prefix stripped: "char_A.png", "glyph_A.png", "letter_A.png"
     * 5. Unicode hex: "uni0041.png", "uni41.png", "uni0998.png"
     * 6. Sequential index fallback
     */
    public static MainActivity.CharacterItem matchCharacterItem(String fileName, List<MainActivity.CharacterItem> characterList, int fallbackIndex) {
        if (fileName == null) {
            return null;
        }

        // Strip file extension
        String name = fileName;
        int dotIdx = name.lastIndexOf('.');
        if (dotIdx > 0) {
            name = name.substring(0, dotIdx);
        }
        name = name.trim();

        // 1. Direct native Bengali character presence anywhere in the name
        for (int idx = 0; idx < name.length(); idx++) {
            char ch = name.charAt(idx);
            if (ch >= 0x0980 && ch <= 0x09FF) {
                return findOrResolveCharacter(ch, characterList);
            }
        }

        // 2. Single character check (case sensitive): "A", "a", "0", "!"
        if (name.length() == 1) {
            char targetChar = name.charAt(0);
            return findOrResolveCharacter(targetChar, characterList);
        }

        String lowerName = name.toLowerCase(Locale.ROOT);

        // 3. Prefixes with single letter: "char_X", "glyph_X", "letter_X", "X_upper", "X_lower"
        if (lowerName.startsWith("char_") || lowerName.startsWith("glyph_") || lowerName.startsWith("letter_")) {
            String remainder = name.substring(name.indexOf('_') + 1);
            if (remainder.length() == 1) {
                char targetChar = remainder.charAt(0);
                return findOrResolveCharacter(targetChar, characterList);
            }
        }

        // 4. Check for Unicode hex naming (e.g. uni0041, uni41, uni0998, u+0998)
        if (lowerName.startsWith("uni") || lowerName.startsWith("u+")) {
            String hex = lowerName.replaceAll("^(uni|u\\+)", "");
            try {
                int codePoint = Integer.parseInt(hex, 16);
                return findOrResolveCharacter((char) codePoint, characterList);
            } catch (NumberFormatException ignored) {}
        }

        // 5. Bengali phonetic transliteration (e.g. "gha", "Gha", "04_gha", "ka", "kha")
        String cleanName = lowerName.replaceAll("^[0-9]+[._-]?", "")
                .replaceAll("^(char|glyph|letter|bn|bangla)[._-]?", "")
                .replaceAll("[._-]?[0-9]+$", "")
                .trim();

        if (BENGALI_TRANSLITERATION.containsKey(cleanName)) {
            char bChar = BENGALI_TRANSLITERATION.get(cleanName);
            return findOrResolveCharacter(bChar, characterList);
        }

        // 6. Fallback sequential index
        if (characterList != null && fallbackIndex >= 0 && fallbackIndex < characterList.size()) {
            return characterList.get(fallbackIndex);
        }

        return null;
    }

    private static MainActivity.CharacterItem findOrResolveCharacter(char targetChar, List<MainActivity.CharacterItem> characterList) {
        if (characterList != null) {
            for (MainActivity.CharacterItem item : characterList) {
                if (item.character == targetChar) {
                    return item;
                }
            }
        }

        // Search across all language scripts (e.g. Bengali script if active script is Latin)
        List<LanguageScript> allScripts = LanguageScript.getAvailableScripts();
        for (LanguageScript script : allScripts) {
            for (MainActivity.CharacterItem item : script.characters) {
                if (item.character == targetChar) {
                    return item;
                }
            }
        }

        // Construct dynamic CharacterItem for this character
        String uId = "uni" + Integer.toHexString((int) targetChar).toLowerCase();
        return new MainActivity.CharacterItem(targetChar, uId, (int) targetChar);
    }

    private static Bitmap loadImageBitmap(Context context, ImageEntry entry) {
        try {
            InputStream is;
            if (entry.uri != null) {
                is = context.getContentResolver().openInputStream(entry.uri);
            } else if (entry.file != null && entry.file.exists()) {
                is = new java.io.FileInputStream(entry.file);
            } else {
                return null;
            }

            if (is == null) return null;

            // Decode bounds first to check size
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            byte[] bytes = readAllBytes(is);
            is.close();

            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, opts);

            // Scale down if extremely large
            int maxDim = Math.max(opts.outWidth, opts.outHeight);
            int inSampleSize = 1;
            while (maxDim / inSampleSize > 1024) {
                inSampleSize *= 2;
            }

            BitmapFactory.Options decodeOpts = new BitmapFactory.Options();
            decodeOpts.inSampleSize = inSampleSize;
            decodeOpts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, decodeOpts);

        } catch (Exception e) {
            Log.e(TAG, "Failed to load bitmap for: " + entry.name, e);
            return null;
        }
    }

    private static List<File> unzipArchive(Context context, Uri zipUri, File destDir) throws Exception {
        List<File> imageFiles = new ArrayList<>();
        InputStream is = context.getContentResolver().openInputStream(zipUri);
        if (is == null) return imageFiles;

        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(is))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            String destCanonicalPath = destDir.getCanonicalPath();

            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                String entryName = entry.getName();
                // Avoid hidden/system files (like __MACOSX)
                if (entryName.startsWith("__MACOSX") || entryName.contains("/.") || entryName.startsWith(".")) {
                    zis.closeEntry();
                    continue;
                }

                String lower = entryName.toLowerCase(Locale.ROOT);
                if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                        || lower.endsWith(".webp") || lower.endsWith(".bmp")) {

                    File outputFile = new File(destDir, new File(entryName).getName());
                    // Zip Slip prevention
                    if (!outputFile.getCanonicalPath().startsWith(destCanonicalPath)) {
                        zis.closeEntry();
                        continue;
                    }

                    try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                    imageFiles.add(outputFile);
                }
                zis.closeEntry();
            }
        }
        return imageFiles;
    }

    private static byte[] readAllBytes(InputStream is) throws Exception {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[8192];
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }

    private static String getFileName(Context context, Uri uri) {
        String result = null;
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception ignored) {}
        }
        if (result == null) {
            result = uri.getPath();
            if (result != null) {
                int cut = result.lastIndexOf('/');
                if (cut != -1) {
                    result = result.substring(cut + 1);
                }
            }
        }
        return result != null ? result : "image_" + System.currentTimeMillis() + ".png";
    }

    private static void deleteRecursive(File fileOrDir) {
        if (fileOrDir.isDirectory()) {
            File[] children = fileOrDir.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        //noinspection ResultOfMethodCallIgnored
        fileOrDir.delete();
    }

    private static void postProgress(Handler handler, OnBatchImportListener listener, int current, int total, char c, String status) {
        handler.post(() -> {
            if (listener != null) listener.onProgress(current, total, c, status);
        });
    }

    private static void postError(Handler handler, OnBatchImportListener listener, String error) {
        handler.post(() -> {
            if (listener != null) listener.onError(error);
        });
    }
}

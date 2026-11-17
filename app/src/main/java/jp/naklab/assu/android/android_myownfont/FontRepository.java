package jp.naklab.assu.android.android_myownfont;

import android.content.Context;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class FontRepository {
    private final Context context;

    public static File getFontsDir(Context context) {
        File dir = new File(context.getExternalFilesDir(null), "fonts");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    public FontRepository(Context context) {
        this.context = context;
        getFontsDir(context); // ensure directory exists
    }

    public File getFontFile(String fontName, String extension) {
        return new File(getFontsDir(context), fontName + "." + extension);
    }

    public boolean writeSvg(String fontName, String svgString) {
        File file = getFontFile(fontName, "svg");
        try (FileOutputStream fos = new FileOutputStream(file, false)) {
            fos.write(svgString.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}

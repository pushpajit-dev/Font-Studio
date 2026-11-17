package jp.naklab.assu.android.android_myownfont._;

import android.content.Context;
import android.util.Log;

import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlPullParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Legacy presenter — kept for reference only, not used in current app.
 */
public class MainFontPresenter {

    final String TAG = "MainFontPresenter";
    Context context;

    public MainFontPresenter(Context context) {
        this.context = context;
    }

    // Legacy XML parse stub — original used org.json.XML which conflicts with Android platform
    public void parseXML2String() {
        try {
            XmlPullParserFactory parserFactory = XmlPullParserFactory.newInstance();
            XmlPullParser parser = parserFactory.newPullParser();
            InputStream is = context.getAssets().open("sample_svg_rialto.svg");
            String xmlString = readTextFile(is);
            Log.d(TAG, xmlString);
        } catch (XmlPullParserException | IOException e) {
            Log.e(TAG, "parseXML2String error: " + e.getMessage());
        }
    }

    private String readTextFile(InputStream inputStream) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buf = new byte[1024];
        int len;
        try {
            while ((len = inputStream.read(buf)) != -1) {
                outputStream.write(buf, 0, len);
            }
            outputStream.close();
            inputStream.close();
        } catch (IOException e) {
            Log.e(TAG, "readTextFile error: " + e.getMessage());
        }
        return outputStream.toString();
    }
}

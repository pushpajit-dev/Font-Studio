package jp.naklab.assu.android.android_myownfont;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LanguageScript {
    public final String id;
    public final String displayName;
    public final String sampleText;
    public final List<MainActivity.CharacterItem> characters;

    public LanguageScript(String id, String displayName, String sampleText, List<MainActivity.CharacterItem> characters) {
        this.id = id;
        this.displayName = displayName;
        this.sampleText = sampleText;
        this.characters = characters;
    }

    public static List<LanguageScript> getAvailableScripts() {
        List<LanguageScript> list = new ArrayList<>();
        list.add(createLatinScript());
        list.add(createBengaliScript());
        list.add(createHindiScript());
        list.add(createTamilScript());
        list.add(createArabicScript());
        list.add(createJapaneseScript());
        return list;
    }

    private static LanguageScript createLatinScript() {
        List<MainActivity.CharacterItem> items = new ArrayList<>();
        // 1. Uppercase A to Z (ASCII 65 to 90)
        for (int c = 65; c <= 90; c++) {
            char ch = (char) c;
            String uid = "uni" + Integer.toHexString(c).toLowerCase();
            items.add(new MainActivity.CharacterItem(ch, uid, c));
        }
        // 2. Lowercase a to z (ASCII 97 to 122)
        for (int c = 97; c <= 122; c++) {
            char ch = (char) c;
            String uid = "uni" + Integer.toHexString(c).toLowerCase();
            items.add(new MainActivity.CharacterItem(ch, uid, c));
        }
        // 3. Digits 0 to 9 (ASCII 48 to 57)
        for (int c = 48; c <= 57; c++) {
            char ch = (char) c;
            String uid = "uni" + Integer.toHexString(c).toLowerCase();
            items.add(new MainActivity.CharacterItem(ch, uid, c));
        }
        // 4. Common symbols and punctuation (ASCII 33-47, 58-64, 91-96, 123-126)
        int[] symbols = {
            33, 34, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, // ! " # $ % & ' ( ) * + , - . /
            58, 59, 60, 61, 62, 63, 64,                                 // : ; < = > ? @
            91, 92, 93, 94, 95, 96,                                     // [ \ ] ^ _ `
            123, 124, 125, 126                                          // { | } ~
        };
        for (int c : symbols) {
            char ch = (char) c;
            String uid = "uni" + Integer.toHexString(c).toLowerCase();
            items.add(new MainActivity.CharacterItem(ch, uid, c));
        }
        return new LanguageScript("latin", "English / Latin (A-Z, 0-9)", "The quick brown fox jumps over the lazy dog 1234567890", items);
    }

    private static LanguageScript createBengaliScript() {
        List<MainActivity.CharacterItem> items = new ArrayList<>();
        // Bengali Vowels: অ আ ই ঈ উ ঊ ঋ এ ঐ ও ঔ
        int[] vowels = { 0x0985, 0x0986, 0x0987, 0x0988, 0x0989, 0x098A, 0x098B, 0x098F, 0x0990, 0x0993, 0x0994 };
        for (int v : vowels) {
            items.add(new MainActivity.CharacterItem((char) v, "uni" + Integer.toHexString(v).toLowerCase(), v));
        }
        // Bengali Consonants: ক খ গ ঘ ঙ চ ছ জ ঝ ঞ ট ঠ ড ঢ ণ ত থ দ ধ ন প ফ ব ভ ম য র ল শ ষ স হ ড় ঢ় য়
        int[] consonants = {
            0x0995, 0x0996, 0x0997, 0x0998, 0x0999, 0x099A, 0x099B, 0x099C, 0x099D, 0x099E,
            0x099F, 0x09A0, 0x09A1, 0x09A2, 0x09A3, 0x09A4, 0x09A5, 0x09A6, 0x09A7, 0x09A8,
            0x09AA, 0x09AB, 0x09AC, 0x09AD, 0x09AE, 0x09AF, 0x09B0, 0x09B2, 0x09B6, 0x09B7,
            0x09B8, 0x09B9, 0x09DC, 0x09DD, 0x09DF
        };
        for (int c : consonants) {
            items.add(new MainActivity.CharacterItem((char) c, "uni" + Integer.toHexString(c).toLowerCase(), c));
        }
        // Bengali Digits: ০ ১ ২ ৩ ৪ ৫ ৬ ৭ ৮ ৯
        for (int d = 0x09E6; d <= 0x09EF; d++) {
            items.add(new MainActivity.CharacterItem((char) d, "uni" + Integer.toHexString(d).toLowerCase(), d));
        }
        // Signs: ং ঃ ঁ
        int[] signs = { 0x0981, 0x0982, 0x0983 };
        for (int s : signs) {
            items.add(new MainActivity.CharacterItem((char) s, "uni" + Integer.toHexString(s).toLowerCase(), s));
        }
        return new LanguageScript("bengali", "Bengali (বাংলা)", "আমি বাংলায় গান গাই, আমি বাংলায় কথা বলি। ১২৩৪৫৬৭৮৯০", items);
    }

    private static LanguageScript createHindiScript() {
        List<MainActivity.CharacterItem> items = new ArrayList<>();
        // Devanagari Vowels: अ आ इ ई उ ऊ ऋ ए ऐ ओ औ
        int[] vowels = { 0x0905, 0x0906, 0x0907, 0x0908, 0x0909, 0x090A, 0x090B, 0x090F, 0x0910, 0x0913, 0x0914 };
        for (int v : vowels) {
            items.add(new MainActivity.CharacterItem((char) v, "uni" + Integer.toHexString(v).toLowerCase(), v));
        }
        // Devanagari Consonants: क ख ग घ ङ च छ ज झ ञ ट ठ ड ढ ण त थ द ध न प फ ब भ म य र ल व श ष स ह
        int[] consonants = {
            0x0915, 0x0916, 0x0917, 0x0918, 0x0919, 0x091A, 0x091B, 0x091C, 0x091D, 0x091E,
            0x091F, 0x0920, 0x0921, 0x0922, 0x0923, 0x0924, 0x0925, 0x0926, 0x0927, 0x0928,
            0x092A, 0x092B, 0x092C, 0x092D, 0x092E, 0x092F, 0x0930, 0x0932, 0x0935, 0x0936,
            0x0937, 0x0938, 0x0939
        };
        for (int c : consonants) {
            items.add(new MainActivity.CharacterItem((char) c, "uni" + Integer.toHexString(c).toLowerCase(), c));
        }
        // Devanagari Digits: ० १ २ ३ ४ ५ ६ ७ ८ ९
        for (int d = 0x0966; d <= 0x096F; d++) {
            items.add(new MainActivity.CharacterItem((char) d, "uni" + Integer.toHexString(d).toLowerCase(), d));
        }
        // Signs: ं ः ँ
        int[] signs = { 0x0901, 0x0902, 0x0903 };
        for (int s : signs) {
            items.add(new MainActivity.CharacterItem((char) s, "uni" + Integer.toHexString(s).toLowerCase(), s));
        }
        return new LanguageScript("hindi", "Hindi / Devanagari (हिन्दी)", "सारे जहाँ से अच्छा हिन्दोस्ताँ हमारा। ०१२३४५६७८९", items);
    }

    private static LanguageScript createTamilScript() {
        List<MainActivity.CharacterItem> items = new ArrayList<>();
        // Tamil Vowels: அ ஆ இ ஈ உ ஊ எ ஏ ஐ ஒ ஓ ஔ
        int[] vowels = { 0x0B85, 0x0B86, 0x0B87, 0x0B88, 0x0B89, 0x0B8A, 0x0B8E, 0x0B8F, 0x0B90, 0x0B92, 0x0B93, 0x0B94 };
        for (int v : vowels) {
            items.add(new MainActivity.CharacterItem((char) v, "uni" + Integer.toHexString(v).toLowerCase(), v));
        }
        // Tamil Consonants: க ங ச ஞ ட ண த ந ப ம ய ர ல வ ழ ள ற ன
        int[] consonants = {
            0x0B95, 0x0B99, 0x0B9A, 0x0B9E, 0x0B9F, 0x0BA3, 0x0BA4, 0x0BA8, 0x0BAA, 0x0BAE,
            0x0BAF, 0x0BB0, 0x0BB2, 0x0BB5, 0x0BB4, 0x0BB3, 0x0BB1, 0x0BA9
        };
        for (int c : consonants) {
            items.add(new MainActivity.CharacterItem((char) c, "uni" + Integer.toHexString(c).toLowerCase(), c));
        }
        // Tamil Digits: ௦ ௧ ௨ ௩ ௪ ௫ ௬ ௭ ௮ ௯
        for (int d = 0x0BE6; d <= 0x0BEF; d++) {
            items.add(new MainActivity.CharacterItem((char) d, "uni" + Integer.toHexString(d).toLowerCase(), d));
        }
        // Aytham: ஃ
        items.add(new MainActivity.CharacterItem((char) 0x0B83, "uni0b83", 0x0B83));
        return new LanguageScript("tamil", "Tamil (தமிழ்)", "யாதும் ஊரே யாவரும் கேளிர். ௧௨௩௪௫", items);
    }

    private static LanguageScript createArabicScript() {
        List<MainActivity.CharacterItem> items = new ArrayList<>();
        // Arabic Letters: ا ب ت ث ج ح خ د ذ ر ز س ش ص ض ط ظ ع غ ف ق ك ل م ن ه و ي
        int[] letters = {
            0x0627, 0x0628, 0x062A, 0x062B, 0x062C, 0x062D, 0x062E, 0x062F, 0x0630, 0x0631,
            0x0632, 0x0633, 0x0634, 0x0635, 0x0636, 0x0637, 0x0638, 0x0639, 0x063A, 0x0641,
            0x0642, 0x0643, 0x0644, 0x0645, 0x0646, 0x0647, 0x0648, 0x064A
        };
        for (int l : letters) {
            items.add(new MainActivity.CharacterItem((char) l, "uni" + Integer.toHexString(l).toLowerCase(), l));
        }
        // Arabic-Indic Digits: ٠ ١ ٢ ٣ ٤ ٥ ٦ ٧ ٨ ٩
        for (int d = 0x0660; d <= 0x0669; d++) {
            items.add(new MainActivity.CharacterItem((char) d, "uni" + Integer.toHexString(d).toLowerCase(), d));
        }
        return new LanguageScript("arabic", "Arabic (العربية)", "أهلا بك في استوديو الخطوط العربية ١٢٣٤٥", items);
    }

    private static LanguageScript createJapaneseScript() {
        List<MainActivity.CharacterItem> items = new ArrayList<>();
        // Hiragana: あいうえおかきくけこさしすせそたちつてとなにぬねのはひふへほまみむめもやゆよらりるれろわをん
        int[] hiragana = {
            0x3042, 0x3044, 0x3046, 0x3048, 0x304A, // a i u e o
            0x304B, 0x304D, 0x304F, 0x3051, 0x3053, // ka ki ku ke ko
            0x3055, 0x3057, 0x3059, 0x305B, 0x305D, // sa shi su se so
            0x305F, 0x3061, 0x3064, 0x3066, 0x3068, // ta chi tsu te to
            0x306A, 0x306B, 0x306C, 0x306D, 0x306E, // na ni nu ne no
            0x306F, 0x3072, 0x3075, 0x3078, 0x307B, // ha hi fu he ho
            0x307E, 0x307F, 0x3080, 0x3081, 0x3082, // ma mi mu me mo
            0x3084, 0x3086, 0x3088,                 // ya yu yo
            0x3089, 0x308A, 0x308B, 0x308C, 0x308D, // ra ri ru re ro
            0x308F, 0x3092, 0x3093                  // wa wo n
        };
        for (int h : hiragana) {
            items.add(new MainActivity.CharacterItem((char) h, "uni" + Integer.toHexString(h).toLowerCase(), h));
        }
        return new LanguageScript("japanese", "Japanese (かな Hiragana)", "こんにちは、フォントスタジオへようこそ！", items);
    }
}

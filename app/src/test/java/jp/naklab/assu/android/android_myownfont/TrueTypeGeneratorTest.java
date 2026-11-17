package jp.naklab.assu.android.android_myownfont;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TrueTypeGeneratorTest {

    @Test
    public void testSvgPathParser() {
        String pathD = "M 100 200 L 300 200 L 300 400 L 100 400 Z";
        List<List<TrueTypeGenerator.Point2D>> contours = TrueTypeGenerator.parseSvgPathToContours(pathD);
        assertNotNull(contours);
        assertEquals(1, contours.size());
        assertEquals(4, contours.get(0).size());
        // Winding normalization may reverse order; just verify the point exists in the contour
        boolean found = false;
        for (TrueTypeGenerator.Point2D pt : contours.get(0)) {
            if (pt.x == 100 && pt.y == 200) { found = true; break; }
        }
        assertTrue("Point (100, 200) should be present in contour", found);
    }

    @Test
    public void testBuildTrueTypeFontBinary() throws Exception {
        List<TrueTypeGenerator.GlyphData> glyphs = new ArrayList<>();

        // Glyph 0: .notdef
        TrueTypeGenerator.GlyphData notdef = new TrueTypeGenerator.GlyphData(0, ".notdef", 500);
        glyphs.add(notdef);

        // Glyph 1: space
        TrueTypeGenerator.GlyphData space = new TrueTypeGenerator.GlyphData(32, "space", 300);
        glyphs.add(space);

        // Glyph 2: 'A'
        TrueTypeGenerator.GlyphData glyphA = new TrueTypeGenerator.GlyphData(65, "uni41", 800);
        glyphA.contours = TrueTypeGenerator.parseSvgPathToContours("M 100 0 L 400 700 L 700 0 L 500 0 L 400 300 L 300 0 Z");
        glyphs.add(glyphA);

        Method buildMethod = TrueTypeGenerator.class.getDeclaredMethod("buildTrueTypeFont", String.class, List.class);
        buildMethod.setAccessible(true);
        byte[] fontBytes = (byte[]) buildMethod.invoke(null, "TestFont", glyphs);

        assertNotNull(fontBytes);
        assertTrue(fontBytes.length > 200);

        // Check TrueType magic in SFNT header (0x00010000)
        assertEquals(0x00, fontBytes[0]);
        assertEquals(0x01, fontBytes[1]);
        assertEquals(0x00, fontBytes[2]);
        assertEquals(0x00, fontBytes[3]);

        // Check number of tables (11 tables including standard DSIG table)
        assertEquals(0, fontBytes[4]);
        assertEquals(11, fontBytes[5]);

        // First table tag alphabetically should be "DSIG"
        assertEquals('D', (char) fontBytes[12]);
        assertEquals('S', (char) fontBytes[13]);
        assertEquals('I', (char) fontBytes[14]);
        assertEquals('G', (char) fontBytes[15]);

        File outTtf = new File("test_out.ttf");
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(outTtf)) {
            fos.write(fontBytes);
        }
    }

    @Test
    public void testLatinScriptOrdering() {
        List<LanguageScript> scripts = LanguageScript.getAvailableScripts();
        LanguageScript latin = null;
        for (LanguageScript s : scripts) {
            if ("latin".equals(s.id)) {
                latin = s;
                break;
            }
        }
        assertNotNull(latin);
        // Verify index 0 is 'A', not '!'
        assertEquals('A', latin.characters.get(0).character);
        assertEquals(65, latin.characters.get(0).unicode);
        assertEquals("uni41", latin.characters.get(0).uId);

        // Verify index 7 is 'H'
        assertEquals('H', latin.characters.get(7).character);
        assertEquals(72, latin.characters.get(7).unicode);
        assertEquals("uni48", latin.characters.get(7).uId);

        // Verify index 25 is 'Z'
        assertEquals('Z', latin.characters.get(25).character);
        assertEquals(90, latin.characters.get(25).unicode);

        // Verify index 26 is 'a'
        assertEquals('a', latin.characters.get(26).character);
        assertEquals(97, latin.characters.get(26).unicode);

        // Verify index 52 is '0'
        assertEquals('0', latin.characters.get(52).character);
        assertEquals(48, latin.characters.get(52).unicode);
    }

    @Test
    public void testMultiGlyphFontMapping() throws Exception {
        List<TrueTypeGenerator.GlyphData> glyphs = new ArrayList<>();
        glyphs.add(new TrueTypeGenerator.GlyphData(0, ".notdef", 500));
        glyphs.add(new TrueTypeGenerator.GlyphData(32, "space", 300));

        TrueTypeGenerator.GlyphData glyphA = new TrueTypeGenerator.GlyphData(65, "uni41", 800);
        glyphA.contours = TrueTypeGenerator.parseSvgPathToContours("M 100 0 L 400 700 L 700 0 L 500 0 L 400 300 L 300 0 Z");
        glyphs.add(glyphA);

        TrueTypeGenerator.GlyphData glyphH = new TrueTypeGenerator.GlyphData(72, "uni48", 800);
        glyphH.contours = TrueTypeGenerator.parseSvgPathToContours("M 100 0 L 100 700 L 250 700 L 250 400 L 550 400 L 550 700 L 700 700 L 700 0 L 550 0 L 550 300 L 250 300 L 250 0 Z");
        glyphs.add(glyphH);

        Method buildMethod = TrueTypeGenerator.class.getDeclaredMethod("buildTrueTypeFont", String.class, List.class);
        buildMethod.setAccessible(true);
        byte[] fontBytes = (byte[]) buildMethod.invoke(null, "AlphabetFont", glyphs);

        assertNotNull(fontBytes);
        assertTrue(fontBytes.length > 500);

        File outTtf = new File("multi_glyph_test.ttf");
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(outTtf)) {
            fos.write(fontBytes);
        }
    }

    @Test
    public void testBatchImageMatcher() {
        List<LanguageScript> scripts = LanguageScript.getAvailableScripts();
        List<MainActivity.CharacterItem> items = scripts.get(0).characters;

        // Exact letter matching
        MainActivity.CharacterItem itemA = BatchImageFontImporter.matchCharacterItem("A.png", items, 0);
        assertNotNull(itemA);
        assertEquals('A', itemA.character);

        MainActivity.CharacterItem itemLowerA = BatchImageFontImporter.matchCharacterItem("a.png", items, 0);
        assertNotNull(itemLowerA);
        assertEquals('a', itemLowerA.character);

        MainActivity.CharacterItem itemDigit5 = BatchImageFontImporter.matchCharacterItem("5.jpg", items, 0);
        assertNotNull(itemDigit5);
        assertEquals('5', itemDigit5.character);

        // Prefixed matching
        MainActivity.CharacterItem itemCharZ = BatchImageFontImporter.matchCharacterItem("char_Z.webp", items, 0);
        assertNotNull(itemCharZ);
        assertEquals('Z', itemCharZ.character);

        MainActivity.CharacterItem itemGlyphB = BatchImageFontImporter.matchCharacterItem("glyph_b.png", items, 0);
        assertNotNull(itemGlyphB);
        assertEquals('b', itemGlyphB.character);

        // Unicode hex matching
        MainActivity.CharacterItem itemUniHex = BatchImageFontImporter.matchCharacterItem("uni0041.png", items, 0);
        assertNotNull(itemUniHex);
        assertEquals('A', itemUniHex.character);

        MainActivity.CharacterItem itemUniShort = BatchImageFontImporter.matchCharacterItem("uni42.png", items, 0);
        assertNotNull(itemUniShort);
        assertEquals('B', itemUniShort.character);

        // Fallback matching
        MainActivity.CharacterItem itemFallback = BatchImageFontImporter.matchCharacterItem("unnamed_random_photo.png", items, 7);
        assertNotNull(itemFallback);
        assertEquals('H', itemFallback.character);

        // Bengali matching (including user-specified Gha 'ঘ')
        MainActivity.CharacterItem itemGha = BatchImageFontImporter.matchCharacterItem("gha.png", items, 0);
        assertNotNull(itemGha);
        assertEquals('ঘ', itemGha.character);

        MainActivity.CharacterItem item04Gha = BatchImageFontImporter.matchCharacterItem("04_Gha.png", items, 0);
        assertNotNull(item04Gha);
        assertEquals('ঘ', item04Gha.character);

        MainActivity.CharacterItem itemNativeGha = BatchImageFontImporter.matchCharacterItem("ঘ.png", items, 0);
        assertNotNull(itemNativeGha);
        assertEquals('ঘ', itemNativeGha.character);
    }
}


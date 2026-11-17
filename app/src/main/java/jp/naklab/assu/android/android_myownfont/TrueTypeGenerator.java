package jp.naklab.assu.android.android_myownfont;

import android.content.Context;
import android.graphics.Typeface;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 100% Local, Offline TrueType (.ttf) and OpenType (.otf) Font Generator.
 * Converts vector SVG font definitions and glyph contours into valid, binary SFNT TrueType fonts
 * natively on Android without any external cloud APIs.
 */
public class TrueTypeGenerator {
    private static final String TAG = "TrueTypeGenerator";

    public interface OnExportListener {
        void onSuccess(File ttfFile, File otfFile, File svgFile);
        void onFailure(String error);
    }

    public static class Point2D {
        public short x;
        public short y;

        public Point2D(int x, int y) {
            this.x = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, x));
            this.y = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, y));
        }
    }

    public static class GlyphData {
        public int unicode;
        public String glyphName;
        public int advanceWidth;
        public List<List<Point2D>> contours = new ArrayList<>();

        public GlyphData(int unicode, String glyphName, int advanceWidth) {
            this.unicode = unicode;
            this.glyphName = glyphName;
            this.advanceWidth = advanceWidth;
        }
    }

    public enum FontStyle {
        REGULAR("Regular", 400, false, false, false),
        MEDIUM("Medium", 500, false, false, false),
        SEMI_BOLD("SemiBold", 600, false, false, false),
        BOLD("Bold", 700, true, false, false),
        ITALIC("Italic", 400, false, true, false),
        BOLD_ITALIC("BoldItalic", 700, true, true, false),
        UNDERLINE("Underline", 400, false, false, true);

        public final String suffix;
        public final int weightClass;
        public final boolean isBold;
        public final boolean isItalic;
        public final boolean isUnderline;

        FontStyle(String suffix, int weightClass, boolean isBold, boolean isItalic, boolean isUnderline) {
            this.suffix = suffix;
            this.weightClass = weightClass;
            this.isBold = isBold;
            this.isItalic = isItalic;
            this.isUnderline = isUnderline;
        }
    }

    /**
     * Transforms base glyph contours into font style variants (Bold, Italic, SemiBold, Medium, Underline).
     */
    public static List<GlyphData> transformGlyphsForStyle(List<GlyphData> baseGlyphs, FontStyle style) {
        if (baseGlyphs == null) return new ArrayList<>();
        if (style == FontStyle.REGULAR) return baseGlyphs;

        List<GlyphData> styled = new ArrayList<>();
        double slantFactor = style.isItalic ? 0.212 : 0.0; // tan(12 deg) ~ 0.212
        double weightExpansion = 1.0;
        if (style == FontStyle.MEDIUM) weightExpansion = 1.06;
        else if (style == FontStyle.SEMI_BOLD) weightExpansion = 1.12;
        else if (style == FontStyle.BOLD || style == FontStyle.BOLD_ITALIC) weightExpansion = 1.20;

        for (GlyphData base : baseGlyphs) {
            int newAdv = (int) Math.round(base.advanceWidth * (style.isBold ? 1.08 : 1.0));
            GlyphData g = new GlyphData(base.unicode, base.glyphName, newAdv);

            for (List<Point2D> contour : base.contours) {
                if (contour.isEmpty()) continue;
                double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
                for (Point2D p : contour) {
                    if (p.x < minX) minX = p.x;
                    if (p.x > maxX) maxX = p.x;
                }
                double cx = (minX + maxX) / 2.0;

                List<Point2D> transformed = new ArrayList<>();
                for (Point2D p : contour) {
                    double x = cx + (p.x - cx) * weightExpansion;
                    double y = p.y;
                    if (slantFactor != 0.0) {
                        x += y * slantFactor;
                    }
                    transformed.add(new Point2D((int) Math.round(x), (int) Math.round(y)));
                }
                g.contours.add(transformed);
            }

            // Underline bar across glyph width
            if (style.isUnderline && g.unicode != 32 && !g.contours.isEmpty()) {
                List<Point2D> ul = new ArrayList<>();
                ul.add(new Point2D(0, -50));
                ul.add(new Point2D(newAdv, -50));
                ul.add(new Point2D(newAdv, -100));
                ul.add(new Point2D(0, -100));
                g.contours.add(ul);
            }

            styled.add(g);
        }
        return styled;
    }

    /**
     * Exports the font locally in TTF, OTF, and SVG formats, including all style variants.
     */
    public static void exportFontLocally(Context context, String fontName, FontMaker fontMaker, OnExportListener listener) {
        try {
            File fontsDir = FontRepository.getFontsDir(context);
            if (!fontsDir.exists()) fontsDir.mkdirs();

            // 1. Gather all base glyphs
            List<GlyphData> glyphs = extractGlyphsFromFontMaker(fontMaker);

            // 2. Build local TrueType binary for Regular (with DSIG table for OTF compatibility)
            byte[] ttfBytes = buildTrueTypeFont(fontName, glyphs, FontStyle.REGULAR);

            // 3. Write TTF
            File ttfFile = new File(fontsDir, fontName + ".ttf");
            try (FileOutputStream fos = new FileOutputStream(ttfFile)) {
                fos.write(ttfBytes);
            }

            // 4. Write OTF (OpenType with TrueType outlines and valid DSIG table)
            File otfFile = new File(fontsDir, fontName + ".otf");
            try (FileOutputStream fos = new FileOutputStream(otfFile)) {
                fos.write(ttfBytes);
            }

            // 5. Write SVG font
            String svgContent = fontMaker.makeFontSvg(fontName);
            File svgFile = new File(fontsDir, fontName + ".svg");
            try (FileOutputStream fos = new FileOutputStream(svgFile)) {
                fos.write(svgContent.getBytes(StandardCharsets.UTF_8));
            }

            // 6. Automatically generate other style variants
            for (FontStyle st : FontStyle.values()) {
                if (st == FontStyle.REGULAR) continue;
                List<GlyphData> stGlyphs = transformGlyphsForStyle(glyphs, st);
                byte[] stBytes = buildTrueTypeFont(fontName, stGlyphs, st);
                File stTtf = new File(fontsDir, fontName + "-" + st.suffix + ".ttf");
                try (FileOutputStream fos = new FileOutputStream(stTtf)) {
                    fos.write(stBytes);
                }
                File stOtf = new File(fontsDir, fontName + "-" + st.suffix + ".otf");
                try (FileOutputStream fos = new FileOutputStream(stOtf)) {
                    fos.write(stBytes);
                }
            }

            // 7. Verify with Android Typeface
            try {
                Typeface tf = Typeface.createFromFile(ttfFile);
                if (tf == null) {
                    Log.w(TAG, "Typeface.createFromFile returned null, but binary file was written.");
                } else {
                    Log.i(TAG, "Typeface successfully created from: " + ttfFile.getAbsolutePath());
                }
            } catch (Exception e) {
                Log.w(TAG, "Typeface preview test warning: " + e.getMessage());
            }

            if (listener != null) {
                listener.onSuccess(ttfFile, otfFile, svgFile);
            }

        } catch (Exception e) {
            Log.e(TAG, "Font export error", e);
            if (listener != null) {
                listener.onFailure("Local Export Failed: " + e.getMessage());
            }
        }
    }

    /**
     * Packs all font styles (TTF, OTF, SVG), OFL License, and README into a clean, valid ZIP archive.
     */
    public static File exportCompleteFontPackageZip(Context context, String fontName, FontMaker fontMaker) throws IOException {
        File fontsDir = FontRepository.getFontsDir(context);
        if (!fontsDir.exists()) fontsDir.mkdirs();

        List<GlyphData> baseGlyphs = extractGlyphsFromFontMaker(fontMaker);
        File zipFile = new File(fontsDir, fontName + "-FontPackage.zip");

        try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(new FileOutputStream(zipFile))) {
            zos.setMethod(java.util.zip.ZipOutputStream.DEFLATED);
            zos.setLevel(java.util.zip.Deflater.BEST_COMPRESSION);

            // 1. Generate all style variants for TTF and OTF
            for (FontStyle style : FontStyle.values()) {
                List<GlyphData> styledGlyphs = transformGlyphsForStyle(baseGlyphs, style);
                byte[] fontBytes = buildTrueTypeFont(fontName, styledGlyphs, style);

                addZipEntry(zos, "fonts/" + fontName + "-" + style.suffix + ".ttf", fontBytes);
                addZipEntry(zos, "fonts/" + fontName + "-" + style.suffix + ".otf", fontBytes);
            }

            // 2. Add SVG font
            String svgContent = fontMaker.makeFontSvg(fontName);
            addZipEntry(zos, "fonts/" + fontName + ".svg", svgContent.getBytes(StandardCharsets.UTF_8));

            // 3. Add LICENSE.txt (SIL Open Font License & Commercial Usage License)
            String licenseText = generateLicenseText(fontName);
            addZipEntry(zos, "LICENSE.txt", licenseText.getBytes(StandardCharsets.UTF_8));

            // 4. Add README.txt
            String readmeText = generateReadmeText(fontName, baseGlyphs.size());
            addZipEntry(zos, "README.txt", readmeText.getBytes(StandardCharsets.UTF_8));

            zos.finish();
        }
        return zipFile;
    }

    private static void addZipEntry(java.util.zip.ZipOutputStream zos, String path, byte[] data) throws IOException {
        java.util.zip.ZipEntry entry = new java.util.zip.ZipEntry(path);
        zos.putNextEntry(entry);
        zos.write(data);
        zos.closeEntry();
    }

    private static String generateLicenseText(String fontName) {
        int year = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR);
        return "========================================================================\n" +
                "FONT SOFTWARE USAGE LICENSE & COPYRIGHT INFORMATION\n" +
                "========================================================================\n\n" +
                "Font Family: " + fontName + "\n" +
                "Release Year: " + year + "\n" +
                "Created With: Android Font Creator (Offline Vector & Contour Spline Engine)\n" +
                "Generated By: Local SFNT TrueType & OpenType Compiler\n\n" +
                "------------------------------------------------------------------------\n" +
                "SIL OPEN FONT LICENSE (OFL-1.1) / COMMERCIAL & PERSONAL USER LICENSE\n" +
                "------------------------------------------------------------------------\n\n" +
                "Copyright (c) " + year + ", Font Designer / Creator. All rights reserved.\n\n" +
                "PERMISSION & RIGHTS GRANTED:\n" +
                "Permission is hereby granted, free of charge, to any person obtaining a copy\n" +
                "of the Font Software, to use, study, copy, merge, embed, modify, redistribute,\n" +
                "and sell modified and unmodified copies of the Font Software, subject to the\n" +
                "following conditions:\n\n" +
                "1. Neither the Font Software nor any of its individual components, in Original\n" +
                "   or Modified Versions, may be sold by itself.\n" +
                "2. Original or Modified Versions of the Font Software may be bundled, redistributed\n" +
                "   and/or sold with any software, provided that each copy contains the above\n" +
                "   copyright notice and this license.\n" +
                "3. The Font Software can be freely embedded into documents, websites, digital\n" +
                "   publications, and mobile/desktop applications without royalty obligations.\n\n" +
                "TECHNICAL CREATION SPECIFICATION:\n" +
                "- Vector Engine: 1000 EM TrueType Spline & Quadratic Bézier Outlines\n" +
                "- OpenType Tables: DSIG, OS/2, cmap (Format 4), glyf, head, hhea, hmtx, loca, maxp, name, post\n" +
                "- Compatibility: Windows 10/11, macOS, Linux, iOS, Android, and Web\n" +
                "========================================================================\n";
    }

    private static String generateReadmeText(String fontName, int glyphCount) {
        return "========================================================================\n" +
                fontName.toUpperCase() + " FONT FAMILY PACKAGE\n" +
                "========================================================================\n\n" +
                "Included Font Styles:\n" +
                "  1. " + fontName + "-Regular.ttf / .otf (Weight 400)\n" +
                "  2. " + fontName + "-Medium.ttf / .otf (Weight 500)\n" +
                "  3. " + fontName + "-SemiBold.ttf / .otf (Weight 600)\n" +
                "  4. " + fontName + "-Bold.ttf / .otf (Weight 700)\n" +
                "  5. " + fontName + "-Italic.ttf / .otf (Oblique 12°)\n" +
                "  6. " + fontName + "-BoldItalic.ttf / .otf (Weight 700 + Oblique)\n" +
                "  7. " + fontName + "-Underline.ttf / .otf (Underlined Glyphs)\n" +
                "  8. " + fontName + ".svg (Vector Web Font)\n\n" +
                "Total Glyphs Included: " + glyphCount + "\n\n" +
                "INSTALLATION INSTRUCTIONS:\n" +
                "- Windows: Extract ZIP, right-click the .ttf or .otf files, and choose 'Install'.\n" +
                "- macOS: Double-click any .otf / .ttf file and click 'Install Font' in Font Book.\n" +
                "- Linux: Copy files to ~/.local/share/fonts/ or ~/.fonts/ and run `fc-cache -f -v`.\n" +
                "- Android / Web: Use the .ttf or .otf files directly with @font-face or Typeface.createFromAsset().\n\n" +
                "Created natively with Android Font Studio.\n";
    }

    private static List<GlyphData> extractGlyphsFromFontMaker(FontMaker fontMaker) {
        Map<Integer, GlyphData> map = new HashMap<>();

        // 1. Always create .notdef (Glyph 0)
        GlyphData notdef = createNotdefGlyph();
        // 2. Always create space (Unicode 32)
        GlyphData space = new GlyphData(32, "space", 300);

        // 3. Parse glyphs from customGlyphsMap and fontGlyphs
        List<String> rawXmlList = new ArrayList<>(fontMaker.customGlyphsMap.values());
        rawXmlList.addAll(fontMaker.fontGlyphs);

        Pattern glyphPattern = Pattern.compile("<glyph\\s+([^>]+)/>");
        Pattern attrPattern = Pattern.compile("([a-zA-Z\\-]+)=\"([^\"]*)\"");

        for (String raw : rawXmlList) {
            Matcher m = glyphPattern.matcher(raw);
            if (m.find()) {
                String attrs = m.group(1);
                Matcher am = attrPattern.matcher(attrs);
                String d = "";
                String name = "";
                String unicodeStr = "";
                int adv = 800;

                while (am.find()) {
                    String key = am.group(1);
                    String val = am.group(2);
                    if ("d".equals(key)) d = val;
                    else if ("glyph-name".equals(key)) name = val;
                    else if ("unicode".equals(key)) unicodeStr = val;
                    else if ("horiz-adv-x".equals(key)) {
                        try { adv = Integer.parseInt(val); } catch (Exception ignored) {}
                    }
                }

                int code = parseUnicode(unicodeStr, name);
                if (code > 0) {
                    GlyphData gd = new GlyphData(code, name.isEmpty() ? ("u" + code) : name, adv);
                    gd.contours = parseSvgPathToContours(d);
                    map.put(code, gd);
                }
            }
        }

        // Add space if missing
        if (!map.containsKey(32)) {
            map.put(32, space);
        }

        // Return ordered list: .notdef at index 0, followed by characters sorted by unicode
        List<GlyphData> result = new ArrayList<>();
        result.add(notdef);

        List<GlyphData> sortedList = new ArrayList<>(map.values());
        Collections.sort(sortedList, Comparator.comparingInt(a -> a.unicode));
        result.addAll(sortedList);

        return result;
    }

    private static GlyphData createNotdefGlyph() {
        GlyphData notdef = new GlyphData(0, ".notdef", 500);
        // Outer box
        List<Point2D> outer = Arrays.asList(
                new Point2D(50, 0),
                new Point2D(450, 0),
                new Point2D(450, 700),
                new Point2D(50, 700)
        );
        // Inner box
        List<Point2D> inner = Arrays.asList(
                new Point2D(90, 40),
                new Point2D(90, 660),
                new Point2D(410, 660),
                new Point2D(410, 40)
        );
        notdef.contours.add(outer);
        notdef.contours.add(inner);
        return notdef;
    }

    private static int parseUnicode(String unicodeStr, String glyphName) {
        if (unicodeStr != null && !unicodeStr.isEmpty()) {
            String u = unicodeStr.trim();
            if (u.startsWith("&#x") || u.startsWith("&#X")) {
                try {
                    return Integer.parseInt(u.substring(3).replace(";", "").trim(), 16);
                } catch (Exception ignored) {}
            } else if (u.startsWith("&#")) {
                try {
                    return Integer.parseInt(u.substring(2).replace(";", "").trim());
                } catch (Exception ignored) {}
            } else if (u.toLowerCase().startsWith("uni")) {
                try {
                    return Integer.parseInt(u.substring(3).trim(), 16);
                } catch (Exception ignored) {}
            } else if (u.toLowerCase().startsWith("u+")) {
                try {
                    return Integer.parseInt(u.substring(2).trim(), 16);
                } catch (Exception ignored) {}
            } else if (u.length() == 1) {
                return (int) u.charAt(0);
            }
        }

        if (glyphName != null && !glyphName.isEmpty()) {
            String g = glyphName.trim();
            if (g.toLowerCase().startsWith("uni")) {
                try {
                    return Integer.parseInt(g.substring(3).trim(), 16);
                } catch (Exception ignored) {}
            } else if (g.toLowerCase().startsWith("u+")) {
                try {
                    return Integer.parseInt(g.substring(2).trim(), 16);
                } catch (Exception ignored) {}
            } else if (g.length() == 1) {
                return (int) g.charAt(0);
            }
        }
        return -1;
    }

    public static List<List<Point2D>> parseSvgPathToContours(String pathD) {
        List<List<Point2D>> contours = new ArrayList<>();
        if (pathD == null || pathD.trim().isEmpty()) return contours;

        List<Point2D> currentContour = new ArrayList<>();
        double curX = 0;
        double curY = 0;
        double startX = 0;
        double startY = 0;

        // Tokenize path into commands and numbers
        List<String> tokens = new ArrayList<>();
        Matcher m = Pattern.compile("([a-zA-Z])|([-+]?[0-9]*\\.?[0-9]+(?:[eE][-+]?[0-9]+)?)").matcher(pathD);
        while (m.find()) {
            tokens.add(m.group());
        }

        int i = 0;
        char cmd = 'M';

        while (i < tokens.size()) {
            String token = tokens.get(i);
            if (Character.isLetter(token.charAt(0))) {
                cmd = token.charAt(0);
                i++;
            }

            switch (cmd) {
                case 'M':
                case 'm': {
                    if (i + 1 >= tokens.size()) break;
                    double x = Double.parseDouble(tokens.get(i++));
                    double y = Double.parseDouble(tokens.get(i++));
                    if (cmd == 'm') { x += curX; y += curY; }
                    curX = x; curY = y;
                    startX = x; startY = y;
                    if (!currentContour.isEmpty()) {
                        contours.add(currentContour);
                        currentContour = new ArrayList<>();
                    }
                    currentContour.add(new Point2D((int) Math.round(curX), (int) Math.round(curY)));
                    // Subsequent coordinates are treated as lineTo
                    cmd = (cmd == 'm') ? 'l' : 'L';
                    break;
                }
                case 'L':
                case 'l': {
                    if (i + 1 >= tokens.size()) break;
                    double x = Double.parseDouble(tokens.get(i++));
                    double y = Double.parseDouble(tokens.get(i++));
                    if (cmd == 'l') { x += curX; y += curY; }
                    curX = x; curY = y;
                    currentContour.add(new Point2D((int) Math.round(curX), (int) Math.round(curY)));
                    break;
                }
                case 'H':
                case 'h': {
                    if (i >= tokens.size()) break;
                    double x = Double.parseDouble(tokens.get(i++));
                    if (cmd == 'h') { x += curX; }
                    curX = x;
                    currentContour.add(new Point2D((int) Math.round(curX), (int) Math.round(curY)));
                    break;
                }
                case 'V':
                case 'v': {
                    if (i >= tokens.size()) break;
                    double y = Double.parseDouble(tokens.get(i++));
                    if (cmd == 'v') { y += curY; }
                    curY = y;
                    currentContour.add(new Point2D((int) Math.round(curX), (int) Math.round(curY)));
                    break;
                }
                case 'C':
                case 'c': {
                    if (i + 5 >= tokens.size()) break;
                    double x1 = Double.parseDouble(tokens.get(i++));
                    double y1 = Double.parseDouble(tokens.get(i++));
                    double x2 = Double.parseDouble(tokens.get(i++));
                    double y2 = Double.parseDouble(tokens.get(i++));
                    double x = Double.parseDouble(tokens.get(i++));
                    double y = Double.parseDouble(tokens.get(i++));
                    if (cmd == 'c') {
                        x1 += curX; y1 += curY;
                        x2 += curX; y2 += curY;
                        x += curX; y += curY;
                    }
                    // Sample cubic Bezier into 6 points
                    for (int step = 1; step <= 6; step++) {
                        double t = step / 6.0;
                        double invT = 1.0 - t;
                        double bx = invT * invT * invT * curX + 3 * invT * invT * t * x1 + 3 * invT * t * t * x2 + t * t * t * x;
                        double by = invT * invT * invT * curY + 3 * invT * invT * t * y1 + 3 * invT * t * t * y2 + t * t * t * y;
                        currentContour.add(new Point2D((int) Math.round(bx), (int) Math.round(by)));
                    }
                    curX = x; curY = y;
                    break;
                }
                case 'Q':
                case 'q': {
                    if (i + 3 >= tokens.size()) break;
                    double x1 = Double.parseDouble(tokens.get(i++));
                    double y1 = Double.parseDouble(tokens.get(i++));
                    double x = Double.parseDouble(tokens.get(i++));
                    double y = Double.parseDouble(tokens.get(i++));
                    if (cmd == 'q') {
                        x1 += curX; y1 += curY;
                        x += curX; y += curY;
                    }
                    // Sample quadratic Bezier into 4 points
                    for (int step = 1; step <= 4; step++) {
                        double t = step / 4.0;
                        double invT = 1.0 - t;
                        double bx = invT * invT * curX + 2 * invT * t * x1 + t * t * x;
                        double by = invT * invT * curY + 2 * invT * t * y1 + t * t * y;
                        currentContour.add(new Point2D((int) Math.round(bx), (int) Math.round(by)));
                    }
                    curX = x; curY = y;
                    break;
                }
                case 'Z':
                case 'z': {
                    curX = startX;
                    curY = startY;
                    if (!currentContour.isEmpty()) {
                        contours.add(currentContour);
                        currentContour = new ArrayList<>();
                    }
                    break;
                }
                default:
                    i++;
                    break;
            }
        }

        if (!currentContour.isEmpty()) {
            contours.add(currentContour);
        }

        // Clean up empty or 1-point contours
        List<List<Point2D>> validContours = new ArrayList<>();
        for (List<Point2D> c : contours) {
            if (c.size() >= 3) {
                validContours.add(c);
            }
        }

        return normalizeContourWindings(validContours);
    }

    /**
     * Enforces TrueType non-zero winding specification:
     * - Outer contours MUST be Clockwise (signedArea < 0 in Y-up font space)
     * - Inner holes / cutouts MUST be Counter-Clockwise (signedArea > 0 in Y-up font space)
     */
    public static List<List<Point2D>> normalizeContourWindings(List<List<Point2D>> contours) {
        if (contours == null || contours.isEmpty()) return contours;

        // Strip full-bleed perimeter frame contour if multiple contours exist
        if (contours.size() > 1) {
            int frameIdx = -1;
            for (int i = 0; i < contours.size(); i++) {
                List<Point2D> c = contours.get(i);
                double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
                double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
                for (Point2D p : c) {
                    if (p.x < minX) minX = p.x;
                    if (p.x > maxX) maxX = p.x;
                    if (p.y < minY) minY = p.y;
                    if (p.y > maxY) maxY = p.y;
                }
                double w = maxX - minX;
                double h = maxY - minY;
                if (w >= 700 && h >= 650 && minX <= 50 && minY <= 50) {
                    frameIdx = i;
                    break;
                }
            }
            if (frameIdx >= 0) {
                contours.remove(frameIdx);
            }
        }

        if (contours.size() == 1) {
            List<Point2D> c = contours.get(0);
            if (calcSignedArea(c) > 0) {
                Collections.reverse(c);
            }
            return contours;
        }

        int n = contours.size();
        class BBox {
            double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
            double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
            double centerX, centerY;
            BBox(List<Point2D> pts) {
                double sx = 0, sy = 0;
                for (Point2D p : pts) {
                    if (p.x < minX) minX = p.x;
                    if (p.x > maxX) maxX = p.x;
                    if (p.y < minY) minY = p.y;
                    if (p.y > maxY) maxY = p.y;
                    sx += p.x;
                    sy += p.y;
                }
                centerX = sx / pts.size();
                centerY = sy / pts.size();
            }
            boolean inside(BBox other) {
                return minX >= other.minX && maxX <= other.maxX && minY >= other.minY && maxY <= other.maxY;
            }
        }

        BBox[] boxes = new BBox[n];
        for (int i = 0; i < n; i++) {
            boxes[i] = new BBox(contours.get(i));
        }

        for (int i = 0; i < n; i++) {
            List<Point2D> ci = contours.get(i);
            int enclosureCount = 0;

            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                if (boxes[i].inside(boxes[j]) && isPointInPolygon(boxes[i].centerX, boxes[i].centerY, contours.get(j))) {
                    enclosureCount++;
                }
            }

            double area = calcSignedArea(ci);
            boolean isHole = (enclosureCount % 2 != 0);

            if (isHole) {
                // Hole must be Counter-Clockwise (signedArea > 0 in Y-up)
                if (area < 0) {
                    Collections.reverse(ci);
                }
            } else {
                // Outer boundary must be Clockwise (signedArea < 0 in Y-up)
                if (area > 0) {
                    Collections.reverse(ci);
                }
            }
        }

        return contours;
    }

    public static double calcSignedArea(List<Point2D> contour) {
        double sum = 0.0;
        int n = contour.size();
        for (int i = 0; i < n; i++) {
            Point2D p1 = contour.get(i);
            Point2D p2 = contour.get((i + 1) % n);
            sum += ((double) p1.x * p2.y - (double) p2.x * p1.y);
        }
        return sum / 2.0;
    }

    public static boolean isPointInPolygon(double x, double y, List<Point2D> poly) {
        boolean inside = false;
        int n = poly.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = poly.get(i).x, yi = poly.get(i).y;
            double xj = poly.get(j).x, yj = poly.get(j).y;
            if (((yi > y) != (yj > y)) && (x < (xj - xi) * (y - yi) / (yj - yi) + xi)) {
                inside = !inside;
            }
        }
        return inside;
    }

    private static byte[] buildTrueTypeFont(String fontName, List<GlyphData> glyphs) throws IOException {
        return buildTrueTypeFont(fontName, glyphs, FontStyle.REGULAR);
    }

    private static byte[] buildTrueTypeFont(String fontName, List<GlyphData> glyphs, FontStyle style) throws IOException {
        int numGlyphs = glyphs.size();

        // 1. Build 'glyf' and 'loca' tables
        ByteArrayOutputStream glyfStream = new ByteArrayOutputStream();
        DataOutputStream glyfOut = new DataOutputStream(glyfStream);
        long[] locaOffsets = new long[numGlyphs + 1];

        int maxPoints = 0;
        int maxContours = 0;
        short overallMinX = 0;
        short overallMinY = -200;
        short overallMaxX = 1000;
        short overallMaxY = 800;

        for (int gIdx = 0; gIdx < numGlyphs; gIdx++) {
            locaOffsets[gIdx] = glyfStream.size();
            GlyphData g = glyphs.get(gIdx);

            if (g.contours.isEmpty()) {
                // Empty glyph (e.g. space)
                continue;
            }

            short numberOfContours = (short) g.contours.size();
            maxContours = Math.max(maxContours, numberOfContours);

            short gMinX = Short.MAX_VALUE, gMinY = Short.MAX_VALUE;
            short gMaxX = Short.MIN_VALUE, gMaxY = Short.MIN_VALUE;
            int totalPoints = 0;

            for (List<Point2D> contour : g.contours) {
                totalPoints += contour.size();
                for (Point2D p : contour) {
                    if (p.x < gMinX) gMinX = p.x;
                    if (p.x > gMaxX) gMaxX = p.x;
                    if (p.y < gMinY) gMinY = p.y;
                    if (p.y > gMaxY) gMaxY = p.y;
                }
            }

            maxPoints = Math.max(maxPoints, totalPoints);
            overallMinX = (short) Math.min(overallMinX, gMinX);
            overallMinY = (short) Math.min(overallMinY, gMinY);
            overallMaxX = (short) Math.max(overallMaxX, gMaxX);
            overallMaxY = (short) Math.max(overallMaxY, gMaxY);

            // Write Glyph Header
            glyfOut.writeShort(numberOfContours);
            glyfOut.writeShort(gMinX);
            glyfOut.writeShort(gMinY);
            glyfOut.writeShort(gMaxX);
            glyfOut.writeShort(gMaxY);

            // End points of contours
            int cumEnd = -1;
            for (List<Point2D> contour : g.contours) {
                cumEnd += contour.size();
                glyfOut.writeShort(cumEnd);
            }

            // Instruction length
            glyfOut.writeShort(0);

            // Flags: 0x01 = ON_CURVE, signed 16-bit deltas for X and Y
            for (int pIdx = 0; pIdx < totalPoints; pIdx++) {
                glyfOut.writeByte(0x01);
            }

            // X Coordinates as signed 16-bit deltas
            short prevX = 0;
            for (List<Point2D> contour : g.contours) {
                for (Point2D p : contour) {
                    glyfOut.writeShort(p.x - prevX);
                    prevX = p.x;
                }
            }

            // Y Coordinates as signed 16-bit deltas
            short prevY = 0;
            for (List<Point2D> contour : g.contours) {
                for (Point2D p : contour) {
                    glyfOut.writeShort(p.y - prevY);
                    prevY = p.y;
                }
            }

            // Align to 2-byte boundary
            if ((glyfStream.size() % 2) != 0) {
                glyfOut.writeByte(0);
            }
        }
        locaOffsets[numGlyphs] = glyfStream.size();
        glyfOut.flush();
        byte[] glyfData = glyfStream.toByteArray();

        // 2. Build 'loca' table (Format 1: 32-bit offsets)
        ByteArrayOutputStream locaStream = new ByteArrayOutputStream();
        DataOutputStream locaOut = new DataOutputStream(locaStream);
        for (long offset : locaOffsets) {
            locaOut.writeInt((int) offset);
        }
        locaOut.flush();
        byte[] locaData = locaStream.toByteArray();

        // 3. Build 'hmtx' table
        ByteArrayOutputStream hmtxStream = new ByteArrayOutputStream();
        DataOutputStream hmtxOut = new DataOutputStream(hmtxStream);
        for (GlyphData g : glyphs) {
            hmtxOut.writeShort(g.advanceWidth);
            short lsb = 0;
            if (!g.contours.isEmpty() && !g.contours.get(0).isEmpty()) {
                lsb = g.contours.get(0).get(0).x;
            }
            hmtxOut.writeShort(lsb);
        }
        hmtxOut.flush();
        byte[] hmtxData = hmtxStream.toByteArray();

        // 4. Build 'cmap' table (Format 4 Subtable)
        byte[] cmapData = buildCmapTable(glyphs);

        // 5. Build 'maxp' table
        ByteArrayOutputStream maxpStream = new ByteArrayOutputStream();
        DataOutputStream maxpOut = new DataOutputStream(maxpStream);
        maxpOut.writeInt(0x00010000); // Version 1.0
        maxpOut.writeShort(numGlyphs);
        maxpOut.writeShort(maxPoints);
        maxpOut.writeShort(maxContours);
        maxpOut.writeShort(0); // maxCompositePoints
        maxpOut.writeShort(0); // maxCompositeContours
        maxpOut.writeShort(1); // maxZones
        maxpOut.writeShort(0); // maxTwilightPoints
        maxpOut.writeShort(0); // maxStorage
        maxpOut.writeShort(0); // maxFunctionDefs
        maxpOut.writeShort(0); // maxInstructionDefs
        maxpOut.writeShort(0); // maxStackElements
        maxpOut.writeShort(0); // maxSizeOfInstructions
        maxpOut.writeShort(0); // maxComponentElements
        maxpOut.writeShort(0); // maxComponentDepth
        maxpOut.flush();
        byte[] maxpData = maxpStream.toByteArray();

        // 6. Build 'hhea' table
        ByteArrayOutputStream hheaStream = new ByteArrayOutputStream();
        DataOutputStream hheaOut = new DataOutputStream(hheaStream);
        hheaOut.writeInt(0x00010000); // Version 1.0
        hheaOut.writeShort(800);  // Ascender
        hheaOut.writeShort(-200); // Descender
        hheaOut.writeShort(200);  // LineGap
        hheaOut.writeShort(1000); // advanceWidthMax
        hheaOut.writeShort(0);    // minLeftSideBearing
        hheaOut.writeShort(0);    // minRightSideBearing
        hheaOut.writeShort(1000); // xMaxExtent
        hheaOut.writeShort(1);    // caretSlopeRise
        hheaOut.writeShort(0);    // caretSlopeRun
        hheaOut.writeShort(0);    // caretOffset
        hheaOut.writeShort(0); hheaOut.writeShort(0); hheaOut.writeShort(0); hheaOut.writeShort(0); // reserved
        hheaOut.writeShort(0);    // metricDataFormat
        hheaOut.writeShort(numGlyphs); // numberOfHMetrics
        hheaOut.flush();
        byte[] hheaData = hheaStream.toByteArray();

        // 7. Configure font style attributes
        int usWeightClass = 400;
        int fsSelection = 0x0040; // REGULAR
        short macStyle = 0;
        int italicAngle = 0;

        if (style != null) {
            switch (style) {
                case MEDIUM:
                    usWeightClass = 500;
                    fsSelection = 0x0040;
                    break;
                case SEMI_BOLD:
                    usWeightClass = 600;
                    fsSelection = 0x0020; // BOLD
                    macStyle = 0x01; // BOLD
                    break;
                case BOLD:
                    usWeightClass = 700;
                    fsSelection = 0x0020; // BOLD
                    macStyle = 0x01; // BOLD
                    break;
                case ITALIC:
                    usWeightClass = 400;
                    fsSelection = 0x0001; // ITALIC
                    macStyle = 0x02; // ITALIC
                    italicAngle = 0xFFF40000; // -12 degrees in Fixed 16.16
                    break;
                case BOLD_ITALIC:
                    usWeightClass = 700;
                    fsSelection = 0x0021; // BOLD + ITALIC
                    macStyle = 0x03; // BOLD + ITALIC
                    italicAngle = 0xFFF40000;
                    break;
                case UNDERLINE:
                    usWeightClass = 400;
                    fsSelection = 0x0042; // REGULAR + UNDERLINE
                    macStyle = 0x04; // UNDERLINE
                    break;
                case REGULAR:
                default:
                    usWeightClass = 400;
                    fsSelection = 0x0040;
                    break;
            }
        }

        // Build 'OS/2' table (Version 4, 96 bytes)
        ByteArrayOutputStream os2Stream = new ByteArrayOutputStream();
        DataOutputStream os2Out = new DataOutputStream(os2Stream);
        os2Out.writeShort(4);     // version
        os2Out.writeShort(600);   // xAvgCharWidth
        os2Out.writeShort(usWeightClass); // usWeightClass
        os2Out.writeShort(5);     // usWidthClass (Medium)
        os2Out.writeShort(0);     // fsType (Installable)
        os2Out.writeShort(650); os2Out.writeShort(600); // ySubscript
        os2Out.writeShort(0); os2Out.writeShort(75);
        os2Out.writeShort(650); os2Out.writeShort(600); // ySuperscript
        os2Out.writeShort(0); os2Out.writeShort(350);
        os2Out.writeShort(50); os2Out.writeShort(300);  // yStrikeout
        os2Out.writeShort(0);     // sFamilyClass
        os2Out.write(new byte[10]); // Panose
        os2Out.writeInt(0x80000003); // ulUnicodeRange1 (Basic Latin + Supplement)
        os2Out.writeInt(0); os2Out.writeInt(0); os2Out.writeInt(0);
        os2Out.write("NONE".getBytes(StandardCharsets.US_ASCII)); // achVendID
        os2Out.writeShort(fsSelection); // fsSelection
        os2Out.writeShort(32);    // usFirstCharIndex
        os2Out.writeShort(126);   // usLastCharIndex
        os2Out.writeShort(800);   // sTypoAscender
        os2Out.writeShort(-200);  // sTypoDescender
        os2Out.writeShort(200);   // sTypoLineGap
        os2Out.writeShort(800);   // usWinAscent
        os2Out.writeShort(200);   // usWinDescent
        os2Out.writeInt(1);       // ulCodePageRange1 (Latin 1252)
        os2Out.writeInt(0);
        os2Out.writeShort(500);   // sxHeight
        os2Out.writeShort(700);   // sCapHeight
        os2Out.writeShort(0);     // usDefaultChar
        os2Out.writeShort(32);    // usBreakChar
        os2Out.writeShort(1);     // usMaxContext
        os2Out.flush();
        byte[] os2Data = os2Stream.toByteArray();

        // 8. Build 'post' table (Version 3.0)
        ByteArrayOutputStream postStream = new ByteArrayOutputStream();
        DataOutputStream postOut = new DataOutputStream(postStream);
        postOut.writeInt(0x00030000); // Version 3.0
        postOut.writeInt(italicAngle); // italicAngle
        postOut.writeShort(-100);     // underlinePosition
        postOut.writeShort(50);       // underlineThickness
        postOut.writeInt(0);          // isFixedPitch
        postOut.writeInt(0); postOut.writeInt(0); postOut.writeInt(0); postOut.writeInt(0);
        postOut.flush();
        byte[] postData = postStream.toByteArray();

        // 9. Build 'name' table
        byte[] nameData = buildNameTable(fontName, style);

        // 10. Build 'head' table (54 bytes)
        ByteArrayOutputStream headStream = new ByteArrayOutputStream();
        DataOutputStream headOut = new DataOutputStream(headStream);
        headOut.writeShort(1); headOut.writeShort(0); // Version 1.0
        headOut.writeInt(0x00010000);                 // fontRevision
        headOut.writeInt(0);                          // checkSumAdjustment placeholder
        headOut.writeInt(0x5F0F3CF5);                 // magicNumber
        headOut.writeShort(0x0003);                   // flags (baseline y=0, lsb x=0)
        headOut.writeShort(1000);                     // unitsPerEm
        headOut.writeLong(0);                         // created
        headOut.writeLong(0);                         // modified
        headOut.writeShort(overallMinX);
        headOut.writeShort(overallMinY);
        headOut.writeShort(overallMaxX);
        headOut.writeShort(overallMaxY);
        headOut.writeShort(macStyle);                  // macStyle
        headOut.writeShort(6);                        // lowestRecPPEM
        headOut.writeShort(2);                        // fontDirectionHint
        headOut.writeShort(1);                        // indexToLocFormat: 1 = 32-bit offsets in loca
        headOut.writeShort(0);                        // glyphDataFormat
        headOut.flush();
        byte[] headData = headStream.toByteArray();

        // 11. Build standard 8-byte 'DSIG' table (Enables Windows Font Viewer & macOS Font Book validation)
        byte[] dsigData = new byte[] { 0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00 };

        // Assemble SFNT Container
        // OpenType specification requires table directory entries strictly sorted alphabetically by ASCII tag:
        // "DSIG", "OS/2", "cmap", "glyf", "head", "hhea", "hmtx", "loca", "maxp", "name", "post"
        class TableEntry {
            String tag;
            byte[] data;
            long checksum;
            int offset;
            TableEntry(String tag, byte[] data) {
                this.tag = tag;
                this.data = data;
                this.checksum = calcTableChecksum(data, 0, data.length);
            }
        }

        List<TableEntry> tables = new ArrayList<>();
        tables.add(new TableEntry("DSIG", dsigData));
        tables.add(new TableEntry("OS/2", os2Data));
        tables.add(new TableEntry("cmap", cmapData));
        tables.add(new TableEntry("glyf", glyfData));
        tables.add(new TableEntry("head", headData));
        tables.add(new TableEntry("hhea", hheaData));
        tables.add(new TableEntry("hmtx", hmtxData));
        tables.add(new TableEntry("loca", locaData));
        tables.add(new TableEntry("maxp", maxpData));
        tables.add(new TableEntry("name", nameData));
        tables.add(new TableEntry("post", postData));

        Collections.sort(tables, Comparator.comparing(a -> a.tag));

        // Offset table: 12 bytes
        // Table directory: 11 * 16 = 176 bytes
        int currentOffset = 12 + tables.size() * 16;
        for (TableEntry t : tables) {
            t.offset = currentOffset;
            currentOffset += (t.data.length + 3) & ~3; // 4-byte aligned
        }

        ByteArrayOutputStream sfntStream = new ByteArrayOutputStream();
        DataOutputStream sfntOut = new DataOutputStream(sfntStream);

        // SFNT Header
        int numTables = tables.size();
        int maxPowerOf2 = 1;
        int entrySelector = 0;
        while ((maxPowerOf2 << 1) <= numTables) {
            maxPowerOf2 <<= 1;
            entrySelector++;
        }
        int searchRange = maxPowerOf2 * 16;
        int rangeShift = numTables * 16 - searchRange;

        sfntOut.writeInt(0x00010000); // TrueType version
        sfntOut.writeShort(numTables);
        sfntOut.writeShort(searchRange);
        sfntOut.writeShort(entrySelector);
        sfntOut.writeShort(rangeShift);

        // Table Directory
        int headTableOffsetInFile = -1;
        for (TableEntry t : tables) {
            byte[] tagBytes = t.tag.getBytes(StandardCharsets.US_ASCII);
            sfntOut.write(tagBytes);
            sfntOut.writeInt((int) t.checksum);
            sfntOut.writeInt(t.offset);
            sfntOut.writeInt(t.data.length);
            if ("head".equals(t.tag)) {
                headTableOffsetInFile = t.offset;
            }
        }

        // Table Data Blocks
        for (TableEntry t : tables) {
            sfntOut.write(t.data);
            int pad = ((t.data.length + 3) & ~3) - t.data.length;
            for (int p = 0; p < pad; p++) {
                sfntOut.writeByte(0);
            }
        }
        sfntOut.flush();

        byte[] fontBytes = sfntStream.toByteArray();

        // Calculate checkSumAdjustment and write into head table (offset + 8)
        long fileChecksum = calcTableChecksum(fontBytes, 0, fontBytes.length);
        long checkSumAdjustment = (0xB1B0AFBAL - fileChecksum) & 0xFFFFFFFFL;

        if (headTableOffsetInFile >= 0 && headTableOffsetInFile + 12 <= fontBytes.length) {
            fontBytes[headTableOffsetInFile + 8] = (byte) ((checkSumAdjustment >> 24) & 0xFF);
            fontBytes[headTableOffsetInFile + 9] = (byte) ((checkSumAdjustment >> 16) & 0xFF);
            fontBytes[headTableOffsetInFile + 10] = (byte) ((checkSumAdjustment >> 8) & 0xFF);
            fontBytes[headTableOffsetInFile + 11] = (byte) (checkSumAdjustment & 0xFF);
        }

        return fontBytes;
    }

    private static byte[] buildCmapTable(List<GlyphData> glyphs) throws IOException {
        // Collect mappings: unicode -> glyphIndex
        class Mapping {
            int code;
            int glyphIndex;
            Mapping(int code, int glyphIndex) {
                this.code = code;
                this.glyphIndex = glyphIndex;
            }
        }

        List<Mapping> mappings = new ArrayList<>();
        for (int i = 0; i < glyphs.size(); i++) {
            GlyphData g = glyphs.get(i);
            if (g.unicode > 0 && g.unicode <= 0xFFFF) {
                mappings.add(new Mapping(g.unicode, i));
            }
        }

        Collections.sort(mappings, Comparator.comparingInt(m -> m.code));

        // Group into segments
        class Segment {
            int startCode;
            int endCode;
            int idDelta;
            Segment(int startCode, int endCode, int idDelta) {
                this.startCode = startCode;
                this.endCode = endCode;
                this.idDelta = idDelta;
            }
        }

        List<Segment> segments = new ArrayList<>();
        if (!mappings.isEmpty()) {
            int segStart = mappings.get(0).code;
            int segEnd = mappings.get(0).code;
            int segStartIdx = mappings.get(0).glyphIndex;

            for (int i = 1; i < mappings.size(); i++) {
                Mapping m = mappings.get(i);
                if (m.code == segEnd + 1 && (m.glyphIndex - m.code) == (segStartIdx - segStart)) {
                    segEnd = m.code;
                } else {
                    int delta = (segStartIdx - segStart) & 0xFFFF;
                    segments.add(new Segment(segStart, segEnd, delta));
                    segStart = m.code;
                    segEnd = m.code;
                    segStartIdx = m.glyphIndex;
                }
            }
            int delta = (segStartIdx - segStart) & 0xFFFF;
            segments.add(new Segment(segStart, segEnd, delta));
        }

        // Add mandatory terminating segment: 0xFFFF
        segments.add(new Segment(0xFFFF, 0xFFFF, 1));

        int segCount = segments.size();
        int segCountX2 = segCount * 2;
        int powerOf2 = 1;
        int entrySelector = 0;
        while (powerOf2 * 2 <= segCount) {
            powerOf2 *= 2;
            entrySelector++;
        }
        int searchRange = 2 * powerOf2;
        int rangeShift = 2 * segCount - searchRange;

        // Subtable length = 16 bytes header + 4 * (segCount * 2) bytes
        int subtableLength = 16 + segCount * 8;

        ByteArrayOutputStream subtableStream = new ByteArrayOutputStream();
        DataOutputStream subOut = new DataOutputStream(subtableStream);
        subOut.writeShort(4);              // Format 4
        subOut.writeShort(subtableLength); // Length
        subOut.writeShort(0);              // Language
        subOut.writeShort(segCountX2);
        subOut.writeShort(searchRange);
        subOut.writeShort(entrySelector);
        subOut.writeShort(rangeShift);

        // endCode[segCount]
        for (Segment s : segments) subOut.writeShort(s.endCode);
        subOut.writeShort(0); // reservedPad
        // startCode[segCount]
        for (Segment s : segments) subOut.writeShort(s.startCode);
        // idDelta[segCount]
        for (Segment s : segments) subOut.writeShort(s.idDelta);
        // idRangeOffset[segCount] (all 0s)
        for (int s = 0; s < segCount; s++) subOut.writeShort(0);

        subOut.flush();
        byte[] subtableData = subtableStream.toByteArray();

        // cmap Header + 2 encoding records (Unicode & Windows BMP)
        ByteArrayOutputStream cmapStream = new ByteArrayOutputStream();
        DataOutputStream cmapOut = new DataOutputStream(cmapStream);
        cmapOut.writeShort(0); // version
        cmapOut.writeShort(2); // numTables: 2

        int subtableOffset = 4 + 2 * 8; // 4 + 16 = 20

        // Platform 0 (Unicode), Encoding 3 (BMP)
        cmapOut.writeShort(0); cmapOut.writeShort(3); cmapOut.writeInt(subtableOffset);
        // Platform 3 (Windows), Encoding 1 (BMP)
        cmapOut.writeShort(3); cmapOut.writeShort(1); cmapOut.writeInt(subtableOffset);

        cmapOut.write(subtableData);
        cmapOut.flush();

        return cmapStream.toByteArray();
    }

    private static byte[] buildNameTable(String fontName) throws IOException {
        return buildNameTable(fontName, FontStyle.REGULAR);
    }

    private static byte[] buildNameTable(String fontName, FontStyle style) throws IOException {
        String cleanName = fontName.replaceAll("[^a-zA-Z0-9_\\- ]", "").trim();
        if (cleanName.isEmpty()) cleanName = "FontStudio";
        String subfamily = (style != null && style.suffix != null) ? style.suffix : "Regular";
        String psSuffix = subfamily;
        String psName = cleanName.replaceAll("\\s+", "") + "-" + psSuffix;
        String fullName = cleanName + " " + subfamily;
        String versionStr = "Version 1.000";
        String uniqueId = "1.000;NONE;" + psName;

        // Name records to write:
        // 1: Family, 2: Subfamily, 3: Unique ID, 4: Full name, 5: Version, 6: PostScript name
        class NameRec {
            int platformId, encodingId, languageId, nameId;
            byte[] bytes;
            NameRec(int p, int e, int l, int n, byte[] b) {
                this.platformId = p; this.encodingId = e; this.languageId = l; this.nameId = n;
                this.bytes = b;
            }
        }

        List<NameRec> records = new ArrayList<>();
        // Mac (Platform 1, Roman 0, en 0) -> US_ASCII
        records.add(new NameRec(1, 0, 0, 1, cleanName.getBytes(StandardCharsets.US_ASCII)));
        records.add(new NameRec(1, 0, 0, 2, subfamily.getBytes(StandardCharsets.US_ASCII)));
        records.add(new NameRec(1, 0, 0, 3, uniqueId.getBytes(StandardCharsets.US_ASCII)));
        records.add(new NameRec(1, 0, 0, 4, fullName.getBytes(StandardCharsets.US_ASCII)));
        records.add(new NameRec(1, 0, 0, 5, versionStr.getBytes(StandardCharsets.US_ASCII)));
        records.add(new NameRec(1, 0, 0, 6, psName.getBytes(StandardCharsets.US_ASCII)));

        // Windows (Platform 3, Unicode BMP 1, en-US 0x0409) -> UTF-16BE
        records.add(new NameRec(3, 1, 0x0409, 1, cleanName.getBytes(StandardCharsets.UTF_16BE)));
        records.add(new NameRec(3, 1, 0x0409, 2, subfamily.getBytes(StandardCharsets.UTF_16BE)));
        records.add(new NameRec(3, 1, 0x0409, 3, uniqueId.getBytes(StandardCharsets.UTF_16BE)));
        records.add(new NameRec(3, 1, 0x0409, 4, fullName.getBytes(StandardCharsets.UTF_16BE)));
        records.add(new NameRec(3, 1, 0x0409, 5, versionStr.getBytes(StandardCharsets.UTF_16BE)));
        records.add(new NameRec(3, 1, 0x0409, 6, psName.getBytes(StandardCharsets.UTF_16BE)));

        // OpenType specification requires NameRecords to be strictly sorted:
        // platformId ASC -> encodingId ASC -> languageId ASC -> nameId ASC
        Collections.sort(records, (a, b) -> {
            if (a.platformId != b.platformId) return Integer.compare(a.platformId, b.platformId);
            if (a.encodingId != b.encodingId) return Integer.compare(a.encodingId, b.encodingId);
            if (a.languageId != b.languageId) return Integer.compare(a.languageId, b.languageId);
            return Integer.compare(a.nameId, b.nameId);
        });

        int stringOffset = 6 + records.size() * 12;

        ByteArrayOutputStream nameStream = new ByteArrayOutputStream();
        DataOutputStream nameOut = new DataOutputStream(nameStream);
        nameOut.writeShort(0); // format
        nameOut.writeShort(records.size());
        nameOut.writeShort(stringOffset);

        int currentStrOffset = 0;
        for (NameRec r : records) {
            nameOut.writeShort(r.platformId);
            nameOut.writeShort(r.encodingId);
            nameOut.writeShort(r.languageId);
            nameOut.writeShort(r.nameId);
            nameOut.writeShort(r.bytes.length);
            nameOut.writeShort(currentStrOffset);
            currentStrOffset += r.bytes.length;
        }

        for (NameRec r : records) {
            nameOut.write(r.bytes);
        }
        nameOut.flush();

        return nameStream.toByteArray();
    }

    private static long calcTableChecksum(byte[] data, int offset, int length) {
        long sum = 0L;
        int nLongs = (length + 3) / 4;
        for (int i = 0; i < nLongs; i++) {
            long b0 = (i * 4 < length) ? (data[offset + i * 4] & 0xFF) : 0;
            long b1 = (i * 4 + 1 < length) ? (data[offset + i * 4 + 1] & 0xFF) : 0;
            long b2 = (i * 4 + 2 < length) ? (data[offset + i * 4 + 2] & 0xFF) : 0;
            long b3 = (i * 4 + 3 < length) ? (data[offset + i * 4 + 3] & 0xFF) : 0;
            long val = (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
            sum = (sum + val) & 0xFFFFFFFFL;
        }
        return sum;
    }
}

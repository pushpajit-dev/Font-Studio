package jp.naklab.assu.android.android_myownfont;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import jp.naklab.assu.android.android_myownfont.services.GeminiFontService;

public class FontSheetDetector {
    private static final String TAG = "FontSheetDetector";

    public interface OnSheetDetectionListener {
        void onProgress(int current, int total, String status);
        void onComplete(List<GeminiFontService.GlyphData> detectedGlyphs);
        void onError(String error);
    }

    private final Handler mainHandler;

    public FontSheetDetector() {
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Extracts individual character bounding boxes from any font sheet or multi-character image.
     * Automatically handles background polarity, merges multi-part letters (like dots on 'i', 'j', '=', ':', '!'),
     * and sorts boxes in natural reading order (row-by-row, left-to-right).
     */
    public List<Rect> detectCharacterBoxes(Bitmap sheetBmp) {
        if (sheetBmp == null || sheetBmp.getWidth() <= 0 || sheetBmp.getHeight() <= 0) {
            return new ArrayList<>();
        }

        Mat mat = new Mat();
        Utils.bitmapToMat(sheetBmp, mat, true);

        Mat gray = new Mat();
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGB2GRAY);

        int cols = gray.cols();
        int rows = gray.rows();

        // Check polarity from 4 corners
        double c1 = gray.get(0, 0)[0];
        double c2 = gray.get(0, cols - 1)[0];
        double c3 = gray.get(rows - 1, 0)[0];
        double c4 = gray.get(rows - 1, cols - 1)[0];
        double cornerAvg = (c1 + c2 + c3 + c4) / 4.0;
        boolean isLight = cornerAvg > 127.0;

        // High contrast normalization
        Core.normalize(gray, gray, 0, 255, Core.NORM_MINMAX);

        // Binarization: Letters become white (255), background black (0)
        Mat binary = new Mat();
        int threshType = isLight ? (Imgproc.THRESH_BINARY_INV | Imgproc.THRESH_OTSU) : Imgproc.THRESH_OTSU;
        Imgproc.threshold(gray, binary, 0, 255, threshType);

        // Morphological closure to bridge broken stroke lines
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(3, 3));
        Imgproc.morphologyEx(binary, binary, Imgproc.MORPH_CLOSE, kernel);

        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierarchy = new Mat();
        Imgproc.findContours(binary, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        int sheetWidth = sheetBmp.getWidth();
        int sheetHeight = sheetBmp.getHeight();
        int minHeight = Math.max(10, (int) (sheetHeight * 0.010));
        int minWidth = Math.max(5, (int) (sheetWidth * 0.005));

        List<Rect> rawBoxes = new ArrayList<>();
        for (MatOfPoint c : contours) {
            Rect r = Imgproc.boundingRect(c);
            // Filter noise and border artifacts: ignore full-bleed borders or image frames
            if (r.height >= minHeight && r.width >= minWidth
                    && r.width < sheetWidth * 0.88 && r.height < sheetHeight * 0.88
                    && (r.width * r.height) < 0.65 * (sheetWidth * sheetHeight)) {
                rawBoxes.add(r);
            }
        }

        if (rawBoxes.isEmpty()) {
            return new ArrayList<>();
        }

        // Merge vertically overlapping/adjacent parts (dots on 'i', 'j', '!', '?', '=', ':', accents)
        List<Rect> mergedBoxes = mergeMultiPartCharacters(rawBoxes);

        // Sort in natural reading order (row by row, left to right)
        return sortBoundingBoxesReadingOrder(mergedBoxes, sheetHeight);
    }

    /**
     * Merges vertically-stacked parts belonging to the same character (e.g. dot and stem of 'i'/'j', bars of '=', ':')
     */
    private List<Rect> mergeMultiPartCharacters(List<Rect> boxes) {
        boolean changed = true;
        List<Rect> current = new ArrayList<>(boxes);

        while (changed) {
            changed = false;
            List<Rect> nextList = new ArrayList<>();
            boolean[] merged = new boolean[current.size()];

            for (int i = 0; i < current.size(); i++) {
                if (merged[i]) continue;
                Rect r1 = current.get(i);

                for (int j = i + 1; j < current.size(); j++) {
                    if (merged[j]) continue;
                    Rect r2 = current.get(j);

                    // Horizontal overlap between r1 and r2
                    int hOverlap = Math.min(r1.x + r1.width, r2.x + r2.width) - Math.max(r1.x, r2.x);
                    int minW = Math.min(r1.width, r2.width);

                    // Vertical distance between r1 and r2
                    int vGap = Math.max(r1.y, r2.y) - Math.min(r1.y + r1.height, r2.y + r2.height);
                    int maxH = Math.max(r1.height, r2.height);

                    // If horizontally aligned and vertically close (like dot above stem)
                    if (hOverlap > 0 && hOverlap >= minW * 0.35 && vGap >= -10 && vGap <= maxH * 0.95) {
                        int nx = Math.min(r1.x, r2.x);
                        int ny = Math.min(r1.y, r2.y);
                        int nw = Math.max(r1.x + r1.width, r2.x + r2.width) - nx;
                        int nh = Math.max(r1.y + r1.height, r2.y + r2.height) - ny;
                        r1 = new Rect(nx, ny, nw, nh);
                        merged[j] = true;
                        changed = true;
                    }
                }
                nextList.add(r1);
            }
            current = nextList;
        }

        return current;
    }

    /**
     * Crops a single character box from the sheet bitmap with 12% breathing room padding.
     */
    public Bitmap cropCharacterBitmap(Bitmap sheetBmp, Rect box) {
        if (sheetBmp == null || box == null) return null;

        int sheetW = sheetBmp.getWidth();
        int sheetH = sheetBmp.getHeight();

        int padX = Math.max(4, (int) (box.width * 0.12));
        int padY = Math.max(4, (int) (box.height * 0.12));

        int x = Math.max(0, box.x - padX);
        int y = Math.max(0, box.y - padY);
        int w = Math.min(sheetW - x, box.width + 2 * padX);
        int h = Math.min(sheetH - y, box.height + 2 * padY);

        if (w <= 0 || h <= 0) return null;

        return Bitmap.createBitmap(sheetBmp, x, y, w, h);
    }

    /**
     * Vectorizes a cropped single-character bitmap into a clean SVG path string.
     * Normalized to font EM square (1000 units), baseline at Y = 0, cap-height ~750, horizAdvX = 800.
     */
    public String vectorizeCroppedBitmap(Bitmap croppedBmp) {
        if (croppedBmp == null) return "";

        Mat mat = new Mat();
        Utils.bitmapToMat(croppedBmp, mat, true);

        Mat gray = new Mat();
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGB2GRAY);

        int cols = gray.cols();
        int rows = gray.rows();
        if (cols <= 0 || rows <= 0) return "";

        double c1 = gray.get(0, 0)[0];
        double c2 = gray.get(0, cols - 1)[0];
        double c3 = gray.get(rows - 1, 0)[0];
        double c4 = gray.get(rows - 1, cols - 1)[0];
        boolean isLight = ((c1 + c2 + c3 + c4) / 4.0) > 127.0;

        Core.normalize(gray, gray, 0, 255, Core.NORM_MINMAX);

        Mat binary = new Mat();
        int threshType = isLight ? (Imgproc.THRESH_BINARY_INV | Imgproc.THRESH_OTSU) : Imgproc.THRESH_OTSU;
        Imgproc.threshold(gray, binary, 0, 255, threshType);

        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(3, 3));
        Imgproc.morphologyEx(binary, binary, Imgproc.MORPH_CLOSE, kernel);

        return vectorizeCroppedMat(binary);
    }

    public void detectAndProcessSheet(Bitmap sheetBmp, List<MainActivity.CharacterItem> targetCharacters, OnSheetDetectionListener listener) {
        if (sheetBmp == null) {
            if (listener != null) listener.onError("Image is null.");
            return;
        }

        new Thread(() -> {
            try {
                postProgress(listener, 10, 100, "Detecting character cells across sheet...");

                List<Rect> sortedBoxes = detectCharacterBoxes(sheetBmp);

                if (sortedBoxes.isEmpty()) {
                    postError(listener, "No distinct character glyphs found in the image sheet.");
                    return;
                }

                postProgress(listener, 40, 100, "Found " + sortedBoxes.size() + " characters. Vectorizing serially...");
                List<GeminiFontService.GlyphData> resultGlyphs = new ArrayList<>();

                int targetCount = (targetCharacters != null && !targetCharacters.isEmpty()) ? targetCharacters.size() : sortedBoxes.size();
                int countToProcess = Math.min(sortedBoxes.size(), targetCount);

                for (int i = 0; i < countToProcess; i++) {
                    Rect r = sortedBoxes.get(i);
                    MainActivity.CharacterItem targetItem = (targetCharacters != null && i < targetCharacters.size()) ? targetCharacters.get(i) : null;

                    String ch = targetItem != null ? String.valueOf(targetItem.character) : String.valueOf((char) (65 + i));
                    String glyphName = targetItem != null ? targetItem.uId : ("uni" + Integer.toHexString(ch.charAt(0)).toLowerCase());
                    String unicode = "&#x" + Integer.toHexString(targetItem != null ? targetItem.unicode : (int) ch.charAt(0)) + ";";

                    Bitmap cropped = cropCharacterBitmap(sheetBmp, r);
                    if (cropped != null) {
                        String pathD = vectorizeCroppedBitmap(cropped);
                        if (!pathD.isEmpty()) {
                            resultGlyphs.add(new GeminiFontService.GlyphData(ch, glyphName, unicode, pathD, 800));
                        }
                    }

                    int pct = 40 + (int) (((i + 1) / (float) countToProcess) * 58);
                    postProgress(listener, pct, 100, "Serially traced '" + ch + "' (" + (i + 1) + "/" + countToProcess + ")");
                }

                mainHandler.post(() -> {
                    if (listener != null) {
                        listener.onComplete(resultGlyphs);
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Error in FontSheetDetector", e);
                postError(listener, "Detection error: " + e.getMessage());
            }
        }).start();
    }

    private List<Rect> sortBoundingBoxesReadingOrder(List<Rect> boxes, int totalHeight) {
        if (boxes.isEmpty()) return boxes;

        double totalH = 0;
        for (Rect r : boxes) totalH += r.height;
        double avgHeight = totalH / boxes.size();
        double rowThreshold = avgHeight * 0.65;

        // Sort primarily by vertical center
        Collections.sort(boxes, (r1, r2) -> {
            int c1 = r1.y + r1.height / 2;
            int c2 = r2.y + r2.height / 2;
            return Integer.compare(c1, c2);
        });

        // Group into rows based on vertical overlap
        List<List<Rect>> rows = new ArrayList<>();
        List<Rect> currentRow = new ArrayList<>();
        currentRow.add(boxes.get(0));
        rows.add(currentRow);

        for (int i = 1; i < boxes.size(); i++) {
            Rect current = boxes.get(i);
            int currentCenterY = current.y + current.height / 2;

            int rowCenterSum = 0;
            for (Rect r : currentRow) rowCenterSum += (r.y + r.height / 2);
            int rowAvgCenterY = rowCenterSum / currentRow.size();

            if (Math.abs(currentCenterY - rowAvgCenterY) < rowThreshold) {
                currentRow.add(current);
            } else {
                currentRow = new ArrayList<>();
                currentRow.add(current);
                rows.add(currentRow);
            }
        }

        // Sort each row horizontally (X position)
        List<Rect> sorted = new ArrayList<>();
        for (List<Rect> row : rows) {
            Collections.sort(row, (r1, r2) -> Integer.compare(r1.x, r2.x));
            sorted.addAll(row);
        }
        return sorted;
    }

    private String vectorizeCroppedMat(Mat cropBinary) {
        // Pre-process: Gaussian blur to smooth anti-aliasing before re-thresholding
        Mat blurred = new Mat();
        Imgproc.GaussianBlur(cropBinary, blurred, new Size(3, 3), 0);

        // Re-threshold after blur (binary is already black-on-white at this point)
        Mat clean = new Mat();
        Imgproc.threshold(blurred, clean, 127, 255, Imgproc.THRESH_BINARY);

        // Morphological OPEN to remove isolated noise specks, then CLOSE to fill gaps
        Mat kernelSmall = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(2, 2));
        Imgproc.morphologyEx(clean, clean, Imgproc.MORPH_OPEN, kernelSmall);
        Mat kernelClose = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(3, 3));
        Imgproc.morphologyEx(clean, clean, Imgproc.MORPH_CLOSE, kernelClose);

        List<MatOfPoint> contours = new ArrayList<>();
        Mat hier = new Mat();
        Imgproc.findContours(clean, contours, hier, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

        if (contours.isEmpty()) {
            // Fallback: try original binary if cleaned version lost everything
            Imgproc.findContours(cropBinary, contours, hier, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);
        }

        if (contours.isEmpty()) return "";

        int cropW = cropBinary.cols();
        int cropH = cropBinary.rows();

        // 1. Filter out contours that correspond to outer image borders / background frame
        List<MatOfPoint> validContours = new ArrayList<>();
        for (MatOfPoint c : contours) {
            if (Imgproc.contourArea(c) < 4) continue; // Keep fine punctuation dots
            Rect bound = Imgproc.boundingRect(c);
            // Ignore any contour that matches the outer image border or spans entire frame
            if (bound.width >= cropW * 0.90 && bound.height >= cropH * 0.90 && Imgproc.contourArea(c) > 0.50 * (cropW * cropH)) {
                continue;
            }
            if (bound.x <= 1 && bound.y <= 1 && bound.x + bound.width >= cropW - 2 && bound.y + bound.height >= cropH - 2) {
                continue;
            }
            validContours.add(c);
        }

        if (validContours.isEmpty()) return "";

        // 2. Compute tight ink bounding box across all valid contours
        int minInkX = cropW, minInkY = cropH, maxInkX = 0, maxInkY = 0;
        for (MatOfPoint c : validContours) {
            Rect b = Imgproc.boundingRect(c);
            if (b.x < minInkX) minInkX = b.x;
            if (b.y < minInkY) minInkY = b.y;
            if (b.x + b.width > maxInkX) maxInkX = b.x + b.width;
            if (b.y + b.height > maxInkY) maxInkY = b.y + b.height;
        }

        int inkW = maxInkX - minInkX;
        int inkH = maxInkY - minInkY;
        if (inkW <= 0 || inkH <= 0) return "";

        // Scale factors to fit within 750x750 box inside font EM square with baseline at Y=0
        double maxDim = Math.max(inkW, inkH);
        double scale = 750.0 / (maxDim > 0 ? maxDim : 1);
        double glyphW = inkW * scale;
        double offsetX = (800.0 - glyphW) / 2.0;

        StringBuilder pathBuilder = new StringBuilder();

        for (MatOfPoint c : validContours) {
            MatOfPoint2f c2f = new MatOfPoint2f(c.toArray());
            MatOfPoint2f approx = new MatOfPoint2f();
            // Reduced epsilon (0.0008) retains much more curve detail vs 0.002
            double epsilon = 0.0008 * Imgproc.arcLength(c2f, true);
            Imgproc.approxPolyDP(c2f, approx, epsilon, true);

            Point[] pts = approx.toArray();
            if (pts.length < 3) continue;

            pathBuilder.append("M ");
            for (Point p : pts) {
                // Font coordinate system: positive Y points UP, baseline at Y=0
                double fontX = offsetX + ((p.x - minInkX) * scale);
                double fontY = (maxInkY - p.y) * scale;
                pathBuilder.append(Math.round(fontX)).append(" ").append(Math.round(fontY)).append(" ");
            }
            pathBuilder.append("Z ");
        }

        return pathBuilder.toString().trim();
    }

    private void postProgress(OnSheetDetectionListener listener, int cur, int total, String status) {
        mainHandler.post(() -> {
            if (listener != null) listener.onProgress(cur, total, status);
        });
    }

    private void postError(OnSheetDetectionListener listener, String error) {
        mainHandler.post(() -> {
            if (listener != null) listener.onError(error);
        });
    }
}

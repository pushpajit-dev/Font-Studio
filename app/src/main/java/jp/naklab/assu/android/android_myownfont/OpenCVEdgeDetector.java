package jp.naklab.assu.android.android_myownfont;

import android.content.Context;
import android.graphics.Bitmap;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

public class OpenCVEdgeDetector {
    public static final int MODEL_OPENCV_ADAPTIVE = 0;
    public static final int MODEL_HIGH_PRECISION_SPLINE = 1;
    public static final int MODEL_NEURAL_TRACER = 2;

    private static int activeModel = MODEL_OPENCV_ADAPTIVE;

    public static void setActiveModel(int model) {
        activeModel = model;
    }

    public static int getActiveModel() {
        return activeModel;
    }

    private List<List<Point>> contour2point(List<MatOfPoint> contour) {
        List<List<Point>> points = new ArrayList<>();
        for (int i = 0; i < contour.size(); i++) {
            points.add(contour.get(i).toList());
        }
        return points;
    }

    public String makeGlyphString(Bitmap bmp) {
        return makeGlyphString(bmp, "uni22", 584, "&#x22;");
    }

    public String makeGlyphString(Bitmap bmp, String glyphName, int horizAdvX, String unicode) {
        String pathD = extractPathDFromBitmap(bmp);
        return "<glyph d=\"" + pathD + "\" glyph-name=\"" + glyphName +
                "\" horiz-adv-x=\"" + horizAdvX +
                "\" unicode=\"" + unicode +
                "\" vert-adv-y=\"1000\" />";
    }

    public String extractPathDFromBitmap(Bitmap bmp) {
        if (bmp == null) return "";
        List<List<Point>> points = detectEdgePoints(bmp);
        if (points.isEmpty()) return "";

        int bmpW = bmp.getWidth();
        int bmpH = bmp.getHeight();

        // Calculate bounding box of detected contours
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (List<Point> pointList : points) {
            for (Point p : pointList) {
                if (p.x < minX) minX = p.x;
                if (p.x > maxX) maxX = p.x;
                if (p.y < minY) minY = p.y;
                if (p.y > maxY) maxY = p.y;
            }
        }

        double charW = maxX - minX;
        double charH = maxY - minY;
        if (charW <= 0 || charH <= 0) return "";

        boolean isFromCanvas = (bmpW == 1000 && bmpH == 1000);

        StringBuilder out = new StringBuilder();

        if (isFromCanvas) {
            // Guideline-mapped canvas coordinate space
            // Baseline at Y=800, Ascent at Y=200
            // Height range = 600 -> scaled to 750 font units
            double scaleY = 750.0 / 600.0; // 1.25
            double baselinePixelY = 800.0;

            // Center horizontally within 800-unit advance width
            double charCenterX = (minX + maxX) / 2.0;
            double targetCenterX = 400.0;
            double scaleX = (charW > 700) ? (700.0 / charW) : 1.0;
            if (scaleX < 1.0) scaleY = Math.min(scaleY, scaleX * 1.25);

            for (List<Point> pointList : points) {
                if (pointList.size() < 3) continue;
                out.append("M ");
                for (Point p : pointList) {
                    double fontX = targetCenterX + (p.x - charCenterX) * scaleX;
                    double fontY = (baselinePixelY - p.y) * scaleY;
                    out.append(Math.round(fontX)).append(" ").append(Math.round(fontY)).append(" ");
                }
                out.append("Z ");
            }
        } else {
            // Normalized bounding box fit for imported reference images or photos
            double maxAllowedH = 750.0;
            double maxAllowedW = 680.0;
            double scale = Math.min(maxAllowedH / Math.max(charH, 1.0), maxAllowedW / Math.max(charW, 1.0));
            double glyphW = charW * scale;
            double offsetX = (800.0 - glyphW) / 2.0;

            for (List<Point> pointList : points) {
                if (pointList.size() < 3) continue;
                out.append("M ");
                for (Point p : pointList) {
                    double fontX = offsetX + (p.x - minX) * scale;
                    double fontY = (maxY - p.y) * scale; // Baseline at Y=0
                    out.append(Math.round(fontX)).append(" ").append(Math.round(fontY)).append(" ");
                }
                out.append("Z ");
            }
        }

        return out.toString().trim();
    }

    // High-precision edge detection with Adaptive Otsu and Morphological Gap Closure
    public List<List<Point>> detectEdgePoints(Bitmap bmp) {
        if (bmp == null) return new ArrayList<>();

        Mat mat = new Mat();
        Utils.bitmapToMat(bmp, mat, true);

        // 1. Convert to Grayscale
        Mat gray = new Mat();
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY);

        int cols = gray.cols();
        int rows = gray.rows();
        if (cols <= 0 || rows <= 0) return new ArrayList<>();

        // 2. Check background polarity (light vs dark) from corners
        double c1 = gray.get(0, 0)[0];
        double c2 = gray.get(0, cols - 1)[0];
        double c3 = gray.get(rows - 1, 0)[0];
        double c4 = gray.get(rows - 1, cols - 1)[0];
        double cornerAvg = (c1 + c2 + c3 + c4) / 4.0;
        boolean isLightBackground = cornerAvg > 127.0;

        // 3. Contrast normalization
        Core.normalize(gray, gray, 0, 255, Core.NORM_MINMAX);

        // 3b. Gaussian blur to smooth anti-aliased brush strokes before thresholding
        Imgproc.GaussianBlur(gray, gray, new Size(3, 3), 0);

        // 4. Thresholding: Letters become white (255), background black (0)
        Mat binary = new Mat();
        int threshFlags = isLightBackground ? (Imgproc.THRESH_BINARY_INV | Imgproc.THRESH_OTSU) : Imgproc.THRESH_OTSU;
        Imgproc.threshold(gray, binary, 0, 255, threshFlags);

        // 5. Morphological closure: bridges anti-aliasing gaps and preserves thin stems
        Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(3, 3));
        Imgproc.morphologyEx(binary, binary, Imgproc.MORPH_CLOSE, kernel);

        if (activeModel == MODEL_NEURAL_TRACER) {
            Mat dilateKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(2, 2));
            Imgproc.dilate(binary, binary, dilateKernel);
        }

        Mat hierarchy = new Mat();
        List<MatOfPoint> contours = new ArrayList<>();
        Imgproc.findContours(binary, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);

        // Fallback: If no contours found, try adaptive thresholding
        if (contours.isEmpty()) {
            Imgproc.adaptiveThreshold(gray, binary, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                    isLightBackground ? Imgproc.THRESH_BINARY_INV : Imgproc.THRESH_BINARY, 25, 10);
            Imgproc.findContours(binary, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);
        }

        // 6. Smoothing with approxPolyDP — reduced epsilon for higher fidelity traces
        double epsilonFactor = (activeModel == MODEL_HIGH_PRECISION_SPLINE) ? 0.0008 : 0.0015;
        List<MatOfPoint> smoothedContours = new ArrayList<>();
        for (MatOfPoint contour : contours) {
            double area = Imgproc.contourArea(contour);
            if (area < 6) continue; // Retain small punctuation dots

            org.opencv.core.Rect bound = Imgproc.boundingRect(contour);
            // CRITICAL: Filter out any contour that represents the entire image border or perimeter frame
            if (bound.width >= cols * 0.90 && bound.height >= rows * 0.90 && area > 0.50 * (cols * rows)) {
                continue;
            }
            if (bound.x <= 1 && bound.y <= 1 && bound.x + bound.width >= cols - 2 && bound.y + bound.height >= rows - 2) {
                continue;
            }

            MatOfPoint2f c2f = new MatOfPoint2f(contour.toArray());
            MatOfPoint2f approx2f = new MatOfPoint2f();
            double epsilon = epsilonFactor * Imgproc.arcLength(c2f, true);
            Imgproc.approxPolyDP(c2f, approx2f, epsilon, true);
            smoothedContours.add(new MatOfPoint(approx2f.toArray()));
        }

        return contour2point(smoothedContours);
    }

    public String buildFallbackPathDFromBitmap(Bitmap bmp) {
        if (bmp == null) return "";
        int w = bmp.getWidth();
        int h = bmp.getHeight();
        if (w <= 0 || h <= 0) return "";

        int minX = w, maxX = 0, minY = h, maxY = 0;
        int darkCount = 0;
        int[] pixels = new int[w * h];
        bmp.getPixels(pixels, 0, w, 0, 0, w, h);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = pixels[y * w + x];
                int r = (c >> 16) & 0xFF;
                int g = (c >> 8) & 0xFF;
                int b = c & 0xFF;
                int brightness = (r * 299 + g * 587 + b * 114) / 1000;
                if (brightness < 200) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                    darkCount++;
                }
            }
        }
        if (darkCount < 5 || minX >= maxX || minY >= maxY) {
            minX = (int) (w * 0.2);
            maxX = (int) (w * 0.8);
            minY = (int) (h * 0.2);
            maxY = (int) (h * 0.8);
        }

        double scale = 650.0 / Math.max(maxX - minX, maxY - minY);
        double offsetX = 400.0 - ((maxX - minX) * scale) / 2.0;
        double offsetY = 150.0;

        int pMinX = (int) Math.round(offsetX);
        int pMaxX = (int) Math.round(offsetX + (maxX - minX) * scale);
        int pMinY = (int) Math.round(offsetY);
        int pMaxY = (int) Math.round(offsetY + (maxY - minY) * scale);

        return "M " + pMinX + " " + pMinY + " L " + pMaxX + " " + pMinY + " L " + pMaxX + " " + pMaxY + " L " + pMinX + " " + pMaxY + " Z";
    }
}

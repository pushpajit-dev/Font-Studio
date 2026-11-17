package jp.naklab.assu.android.android_myownfont;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class FontCanvas extends View {
    public static final int TOOL_PEN = 0;
    public static final int TOOL_LINE = 1;
    public static final int TOOL_BEZIER = 2;
    public static final int TOOL_ERASER = 3;

    private int currentTool = TOOL_PEN;
    private int currentColor = 0xFF000000;
    private float currentStrokeWidth = 32f;
    private boolean showGuidelines = true;

    private float touchDownX, touchDownY;
    private float prevTouchX, prevTouchY;

    public static class Stroke {
        public Path path;
        public Paint paint;
        public boolean isEraser;

        public Stroke(Path path, Paint paint, boolean isEraser) {
            this.path = path;
            this.paint = paint;
            this.isEraser = isEraser;
        }
    }

    private final List<Stroke> strokes = new ArrayList<>();
    private Stroke currentStroke;

    private Paint guideLinePaint;
    private Paint baselinePaint;
    private Paint rulerPaint;
    private Paint gridPointPaint;

    public interface OnCanvasTouchListener {
        void onTouchStarted();
        void onTouchEnded();
    }

    private OnCanvasTouchListener touchListener;

    public void setOnCanvasTouchListener(OnCanvasTouchListener listener) {
        this.touchListener = listener;
    }

    public FontCanvas(Context context) {
        this(context, null);
    }

    public FontCanvas(Context context, AttributeSet attrs) {
        super(context, attrs);
        initPaints();
    }

    private void initPaints() {
        guideLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        guideLinePaint.setColor(0x55008577);
        guideLinePaint.setStyle(Paint.Style.STROKE);
        guideLinePaint.setStrokeWidth(2f);
        guideLinePaint.setPathEffect(new DashPathEffect(new float[]{10, 10}, 0));

        baselinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        baselinePaint.setColor(0xCCBA1A1A); // Red baseline
        baselinePaint.setStyle(Paint.Style.STROKE);
        baselinePaint.setStrokeWidth(3f);

        rulerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        rulerPaint.setColor(0x8874777F);
        rulerPaint.setTextSize(22f);
        rulerPaint.setStyle(Paint.Style.FILL_AND_STROKE);

        gridPointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPointPaint.setColor(0x3374777F);
        gridPointPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();

        // 1. Draw Typography Guidelines & Rulers
        if (showGuidelines && w > 0 && h > 0) {
            drawGuidelines(canvas, w, h);
        }

        // 2. Draw user strokes inside a layer so PorterDuff.CLEAR eraser works cleanly
        if (w > 0 && h > 0) {
            int layer = canvas.saveLayer(0, 0, w, h, null);
            for (Stroke s : strokes) {
                canvas.drawPath(s.path, s.paint);
            }
            canvas.restoreToCount(layer);
        }
    }

    private void drawGuidelines(Canvas canvas, int w, int h) {
        float baselineY = h * 0.80f;   // Baseline (y=200 font units)
        float xHeightY = h * 0.50f;    // Mean line / x-height
        float capHeightY = h * 0.30f;  // Cap height
        float ascentY = h * 0.20f;     // Ascent line (y=800 font units)
        float descentY = h * 0.95f;    // Descent line

        float leftBearing = w * 0.12f;
        float rightBearing = w * 0.88f;

        // Draw Safe Points Grid
        for (float gx = leftBearing; gx <= rightBearing; gx += (rightBearing - leftBearing) / 4f) {
            for (float gy = ascentY; gy <= baselineY; gy += (baselineY - ascentY) / 4f) {
                canvas.drawCircle(gx, gy, 3f, gridPointPaint);
            }
        }

        // Draw Ascent line
        canvas.drawLine(0, ascentY, w, ascentY, guideLinePaint);
        canvas.drawText("Ascent", 10, ascentY - 4, rulerPaint);

        // Draw Cap-height
        canvas.drawLine(0, capHeightY, w, capHeightY, guideLinePaint);
        canvas.drawText("Cap", 10, capHeightY - 4, rulerPaint);

        // Draw Mean line / x-height
        canvas.drawLine(0, xHeightY, w, xHeightY, guideLinePaint);
        canvas.drawText("x-height", 10, xHeightY - 4, rulerPaint);

        // Draw Baseline (solid red)
        canvas.drawLine(0, baselineY, w, baselineY, baselinePaint);
        canvas.drawText("Baseline", 10, baselineY - 6, rulerPaint);

        // Draw Descent line
        canvas.drawLine(0, descentY, w, descentY, guideLinePaint);
        canvas.drawText("Descent", 10, descentY - 4, rulerPaint);

        // Draw Left & Right Side Bearings
        canvas.drawLine(leftBearing, 0, leftBearing, h, guideLinePaint);
        canvas.drawLine(rightBearing, 0, rightBearing, h, guideLinePaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (touchListener != null) {
                    touchListener.onTouchStarted();
                }
                // Disallow ScrollView/ViewPager from hijacking drawing gesture
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }

                touchDownX = x;
                touchDownY = y;
                prevTouchX = x;
                prevTouchY = y;

                Path path = new Path();
                path.moveTo(x, y);

                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeJoin(Paint.Join.ROUND);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setStrokeWidth(currentStrokeWidth);

                if (currentTool == TOOL_ERASER) {
                    p.setColor(0x00000000);
                    p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                    p.setStrokeWidth(currentStrokeWidth * 1.8f);
                    currentStroke = new Stroke(path, p, true);
                } else {
                    p.setColor(currentColor);
                    currentStroke = new Stroke(path, p, false);
                }

                strokes.add(currentStroke);
                invalidate();
                break;

            case MotionEvent.ACTION_MOVE:
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                if (currentStroke != null) {
                    if (currentTool == TOOL_LINE) {
                        currentStroke.path.reset();
                        currentStroke.path.moveTo(touchDownX, touchDownY);
                        currentStroke.path.lineTo(x, y);
                    } else if (currentTool == TOOL_BEZIER) {
                        float midX = (prevTouchX + x) / 2f;
                        float midY = (prevTouchY + y) / 2f;
                        currentStroke.path.quadTo(prevTouchX, prevTouchY, midX, midY);
                        prevTouchX = x;
                        prevTouchY = y;
                    } else {
                        currentStroke.path.lineTo(x, y);
                    }
                    invalidate();
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (currentStroke != null) {
                    if (currentTool == TOOL_LINE) {
                        currentStroke.path.reset();
                        currentStroke.path.moveTo(touchDownX, touchDownY);
                        currentStroke.path.lineTo(x, y);
                        invalidate();
                    } else if (currentTool == TOOL_BEZIER) {
                        currentStroke.path.lineTo(x, y);
                        invalidate();
                    }
                }
                if (touchListener != null) {
                    touchListener.onTouchEnded();
                }
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                break;
        }

        return true;
    }

    public void setToolMode(int toolMode) {
        this.currentTool = toolMode;
    }

    public void setPenColor(int color) {
        this.currentColor = color;
    }

    public void setStrokeWidth(float width) {
        this.currentStrokeWidth = width;
    }

    public void toggleGuidelines(boolean show) {
        this.showGuidelines = show;
        invalidate();
    }

    public void clear() {
        strokes.clear();
        invalidate();
    }

    public void undo() {
        if (!strokes.isEmpty()) {
            strokes.remove(strokes.size() - 1);
            invalidate();
        }
    }

    public boolean isEmpty() {
        return strokes.isEmpty();
    }

    public Bitmap getDrawingBitmap() {
        int w = getWidth() > 0 ? getWidth() : 1000;
        int h = getHeight() > 0 ? getHeight() : 1000;

        // Render to normalized 1000x1000 font coordinate space
        Bitmap result = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(result);
        c.drawColor(0xFFFFFFFF); // White background

        float scaleX = 1000f / w;
        float scaleY = 1000f / h;
        c.scale(scaleX, scaleY);

        int layer = c.saveLayer(0, 0, w, h, null);
        for (Stroke s : strokes) {
            if (s.isEraser) {
                c.drawPath(s.path, s.paint);
            } else {
                Paint inkPaint = new Paint(s.paint);
                inkPaint.setColor(0xFF000000); // Solid black ink for clean OpenCV vector extraction
                c.drawPath(s.path, inkPaint);
            }
        }
        c.restoreToCount(layer);

        return result;
    }
}

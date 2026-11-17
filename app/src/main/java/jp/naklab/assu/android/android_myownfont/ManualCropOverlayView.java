package jp.naklab.assu.android.android_myownfont;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.Nullable;

public class ManualCropOverlayView extends View {

    public interface OnCropBoxChangeListener {
        void onCropBoxChanged(float normX, float normY, float normSize);
    }

    private Bitmap sourceBitmap;
    private final RectF bmpDstRect = new RectF();
    private final RectF cropRect = new RectF();

    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint scrimPaint = new Paint();
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private static final int DRAG_NONE = 0;
    private static final int DRAG_BODY = 1;
    private static final int DRAG_TOP_LEFT = 2;
    private static final int DRAG_TOP_RIGHT = 3;
    private static final int DRAG_BOTTOM_LEFT = 4;
    private static final int DRAG_BOTTOM_RIGHT = 5;

    private int dragMode = DRAG_NONE;
    private float touchStartX, touchStartY;
    private final RectF startCropRect = new RectF();

    private org.opencv.core.Rect initialBmpBox = null;
    private static final float HANDLE_RADIUS = 32f;
    private static final float MIN_CROP_SIZE = 50f;

    // Zoom & Pan state
    private float currentZoom = 1.0f;
    private float panOffsetX = 0f;
    private float panOffsetY = 0f;
    private float lastPanTouchX, lastPanTouchY;
    private boolean isPanning = false;

    private final Matrix displayMatrix = new Matrix();
    private final Matrix inverseMatrix = new Matrix();
    private ScaleGestureDetector scaleDetector;
    private OnCropBoxChangeListener cropBoxChangeListener;

    public ManualCropOverlayView(Context context) {
        this(context, null);
    }

    public ManualCropOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        scrimPaint.setColor(0xBB000000);
        scrimPaint.setStyle(Paint.Style.FILL);

        borderPaint.setColor(0xFF00C4B4); // Vibrant cyan/teal
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(4f);

        handlePaint.setColor(0xFFFFFFFF);
        handlePaint.setStyle(Paint.Style.FILL);

        gridPaint.setColor(0x5500C4B4);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1.5f);

        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                float factor = detector.getScaleFactor();
                setZoom(currentZoom * factor);
                return true;
            }
        });
    }

    public void setOnCropBoxChangeListener(OnCropBoxChangeListener listener) {
        this.cropBoxChangeListener = listener;
    }

    public void setBitmap(Bitmap bitmap, @Nullable org.opencv.core.Rect initialBox) {
        this.sourceBitmap = bitmap;
        this.initialBmpBox = initialBox;
        resetZoom();
        requestLayout();
        invalidate();
    }

    public void zoomIn() {
        setZoom(currentZoom * 1.25f);
    }

    public void zoomOut() {
        setZoom(currentZoom / 1.25f);
    }

    public void resetZoom() {
        currentZoom = 1.0f;
        panOffsetX = 0f;
        panOffsetY = 0f;
        updateMatrices();
        invalidate();
    }

    public void setZoom(float zoom) {
        currentZoom = Math.max(1.0f, Math.min(zoom, 4.5f));
        if (currentZoom == 1.0f) {
            panOffsetX = 0f;
            panOffsetY = 0f;
        }
        updateMatrices();
        invalidate();
    }

    private void updateMatrices() {
        displayMatrix.reset();
        displayMatrix.postTranslate(panOffsetX, panOffsetY);
        displayMatrix.postScale(currentZoom, currentZoom, getWidth() / 2f, getHeight() / 2f);
        displayMatrix.invert(inverseMatrix);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        computeBmpAndCropRect();
        updateMatrices();
    }

    private void computeBmpAndCropRect() {
        if (sourceBitmap == null || getWidth() <= 0 || getHeight() <= 0) return;

        int viewW = getWidth();
        int viewH = getHeight();
        int bw = sourceBitmap.getWidth();
        int bh = sourceBitmap.getHeight();

        float scale = Math.min((float) viewW / bw, (float) viewH / bh);
        float destW = bw * scale;
        float destH = bh * scale;
        float destX = (viewW - destW) / 2f;
        float destY = (viewH - destH) / 2f;

        bmpDstRect.set(destX, destY, destX + destW, destY + destH);

        if (initialBmpBox != null && initialBmpBox.width > 0 && initialBmpBox.height > 0) {
            float cLeft = destX + (initialBmpBox.x * scale);
            float cTop = destY + (initialBmpBox.y * scale);
            float cRight = cLeft + (initialBmpBox.width * scale);
            float cBottom = cTop + (initialBmpBox.height * scale);
            cropRect.set(
                    Math.max(bmpDstRect.left, cLeft),
                    Math.max(bmpDstRect.top, cTop),
                    Math.min(bmpDstRect.right, cRight),
                    Math.min(bmpDstRect.bottom, cBottom)
            );
        } else {
            // Default center 35% crop box
            float size = Math.min(destW, destH) * 0.35f;
            float cx = bmpDstRect.centerX();
            float cy = bmpDstRect.centerY();
            cropRect.set(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2);
        }
        notifyCropBoxChanged();
    }

    public void setCropPosition(float normX, float normY) {
        if (bmpDstRect.width() <= 0 || bmpDstRect.height() <= 0) return;
        float w = cropRect.width();
        float h = cropRect.height();

        float targetCx = bmpDstRect.left + normX * bmpDstRect.width();
        float targetCy = bmpDstRect.top + normY * bmpDstRect.height();

        float newL = targetCx - w / 2f;
        float newT = targetCy - h / 2f;
        float newR = newL + w;
        float newB = newT + h;

        if (newL < bmpDstRect.left) {
            newR += (bmpDstRect.left - newL);
            newL = bmpDstRect.left;
        }
        if (newT < bmpDstRect.top) {
            newB += (bmpDstRect.top - newT);
            newT = bmpDstRect.top;
        }
        if (newR > bmpDstRect.right) {
            newL -= (newR - bmpDstRect.right);
            newR = bmpDstRect.right;
        }
        if (newB > bmpDstRect.bottom) {
            newT -= (newB - bmpDstRect.bottom);
            newB = bmpDstRect.bottom;
        }

        cropRect.set(newL, newT, newR, newB);
        invalidate();
    }

    public void setCropSize(float normSize) {
        if (bmpDstRect.width() <= 0 || bmpDstRect.height() <= 0) return;
        float maxDim = Math.min(bmpDstRect.width(), bmpDstRect.height());
        float targetSize = Math.max(MIN_CROP_SIZE, normSize * maxDim);

        float cx = cropRect.centerX();
        float cy = cropRect.centerY();

        float newL = cx - targetSize / 2f;
        float newT = cy - targetSize / 2f;
        float newR = cx + targetSize / 2f;
        float newB = cy + targetSize / 2f;

        if (newL < bmpDstRect.left) newL = bmpDstRect.left;
        if (newT < bmpDstRect.top) newT = bmpDstRect.top;
        if (newR > bmpDstRect.right) newR = bmpDstRect.right;
        if (newB > bmpDstRect.bottom) newB = bmpDstRect.bottom;

        cropRect.set(newL, newT, newR, newB);
        invalidate();
    }

    private void notifyCropBoxChanged() {
        if (cropBoxChangeListener != null && bmpDstRect.width() > 0 && bmpDstRect.height() > 0) {
            float normX = (cropRect.centerX() - bmpDstRect.left) / bmpDstRect.width();
            float normY = (cropRect.centerY() - bmpDstRect.top) / bmpDstRect.height();
            float normSize = cropRect.width() / Math.min(bmpDstRect.width(), bmpDstRect.height());
            cropBoxChangeListener.onCropBoxChanged(
                    Math.max(0f, Math.min(1f, normX)),
                    Math.max(0f, Math.min(1f, normY)),
                    Math.max(0.05f, Math.min(1f, normSize))
            );
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (sourceBitmap == null || sourceBitmap.isRecycled()) return;

        canvas.save();
        canvas.concat(displayMatrix);

        // 1. Draw scaled source image
        canvas.drawBitmap(sourceBitmap, null, bmpDstRect, bitmapPaint);

        // 2. Draw darkened scrim outside cropRect
        canvas.drawRect(bmpDstRect.left, bmpDstRect.top, bmpDstRect.right, cropRect.top, scrimPaint);
        canvas.drawRect(bmpDstRect.left, cropRect.bottom, bmpDstRect.right, bmpDstRect.bottom, scrimPaint);
        canvas.drawRect(bmpDstRect.left, cropRect.top, cropRect.left, cropRect.bottom, scrimPaint);
        canvas.drawRect(cropRect.right, cropRect.top, bmpDstRect.right, cropRect.bottom, scrimPaint);

        // 3. Draw grid lines (rule of thirds inside crop)
        float thirdW = cropRect.width() / 3f;
        float thirdH = cropRect.height() / 3f;
        canvas.drawLine(cropRect.left + thirdW, cropRect.top, cropRect.left + thirdW, cropRect.bottom, gridPaint);
        canvas.drawLine(cropRect.left + 2 * thirdW, cropRect.top, cropRect.left + 2 * thirdW, cropRect.bottom, gridPaint);
        canvas.drawLine(cropRect.left, cropRect.top + thirdH, cropRect.right, cropRect.top + thirdH, gridPaint);
        canvas.drawLine(cropRect.left, cropRect.top + 2 * thirdH, cropRect.right, cropRect.top + 2 * thirdH, gridPaint);

        // 4. Draw crop frame border
        canvas.drawRect(cropRect, borderPaint);

        // 5. Draw 4 corner handles
        drawHandle(canvas, cropRect.left, cropRect.top);
        drawHandle(canvas, cropRect.right, cropRect.top);
        drawHandle(canvas, cropRect.left, cropRect.bottom);
        drawHandle(canvas, cropRect.right, cropRect.bottom);

        canvas.restore();
    }

    private void drawHandle(Canvas canvas, float cx, float cy) {
        handlePaint.setColor(0xFF00C4B4);
        canvas.drawCircle(cx, cy, 20f, handlePaint);
        handlePaint.setColor(Color.WHITE);
        canvas.drawCircle(cx, cy, 14f, handlePaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (scaleDetector != null) {
            scaleDetector.onTouchEvent(event);
        }

        // Two-pointer pinch & pan
        if (event.getPointerCount() > 1) {
            dragMode = DRAG_NONE;
            if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
                lastPanTouchX = (event.getX(0) + event.getX(1)) / 2f;
                lastPanTouchY = (event.getY(0) + event.getY(1)) / 2f;
                isPanning = true;
            } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE && isPanning) {
                float curX = (event.getX(0) + event.getX(1)) / 2f;
                float curY = (event.getY(0) + event.getY(1)) / 2f;
                panOffsetX += (curX - lastPanTouchX);
                panOffsetY += (curY - lastPanTouchY);
                lastPanTouchX = curX;
                lastPanTouchY = curY;
                updateMatrices();
                invalidate();
            }
            return true;
        }

        float rawX = event.getX();
        float rawY = event.getY();

        // Map touch into unscaled canvas coordinate space
        float[] pts = new float[]{rawX, rawY};
        inverseMatrix.mapPoints(pts);
        float x = pts[0];
        float y = pts[1];

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                touchStartX = x;
                touchStartY = y;
                startCropRect.set(cropRect);
                dragMode = getHitTest(x, y);
                return true;

            case MotionEvent.ACTION_MOVE:
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                float dx = x - touchStartX;
                float dy = y - touchStartY;

                if (dragMode == DRAG_BODY) {
                    float newL = startCropRect.left + dx;
                    float newT = startCropRect.top + dy;
                    float newR = startCropRect.right + dx;
                    float newB = startCropRect.bottom + dy;

                    if (newL < bmpDstRect.left) {
                        newR += (bmpDstRect.left - newL);
                        newL = bmpDstRect.left;
                    }
                    if (newT < bmpDstRect.top) {
                        newB += (bmpDstRect.top - newT);
                        newT = bmpDstRect.top;
                    }
                    if (newR > bmpDstRect.right) {
                        newL -= (newR - bmpDstRect.right);
                        newR = bmpDstRect.right;
                    }
                    if (newB > bmpDstRect.bottom) {
                        newT -= (newB - bmpDstRect.bottom);
                        newB = bmpDstRect.bottom;
                    }

                    cropRect.set(newL, newT, newR, newB);
                    notifyCropBoxChanged();
                    invalidate();

                } else if (dragMode == DRAG_TOP_LEFT) {
                    float newL = Math.max(bmpDstRect.left, Math.min(startCropRect.left + dx, cropRect.right - MIN_CROP_SIZE));
                    float newT = Math.max(bmpDstRect.top, Math.min(startCropRect.top + dy, cropRect.bottom - MIN_CROP_SIZE));
                    cropRect.left = newL;
                    cropRect.top = newT;
                    notifyCropBoxChanged();
                    invalidate();

                } else if (dragMode == DRAG_TOP_RIGHT) {
                    float newR = Math.min(bmpDstRect.right, Math.max(startCropRect.right + dx, cropRect.left + MIN_CROP_SIZE));
                    float newT = Math.max(bmpDstRect.top, Math.min(startCropRect.top + dy, cropRect.bottom - MIN_CROP_SIZE));
                    cropRect.right = newR;
                    cropRect.top = newT;
                    notifyCropBoxChanged();
                    invalidate();

                } else if (dragMode == DRAG_BOTTOM_LEFT) {
                    float newL = Math.max(bmpDstRect.left, Math.min(startCropRect.left + dx, cropRect.right - MIN_CROP_SIZE));
                    float newB = Math.min(bmpDstRect.bottom, Math.max(startCropRect.bottom + dy, cropRect.top + MIN_CROP_SIZE));
                    cropRect.left = newL;
                    cropRect.bottom = newB;
                    notifyCropBoxChanged();
                    invalidate();

                } else if (dragMode == DRAG_BOTTOM_RIGHT) {
                    float newR = Math.min(bmpDstRect.right, Math.max(startCropRect.right + dx, cropRect.left + MIN_CROP_SIZE));
                    float newB = Math.min(bmpDstRect.bottom, Math.max(startCropRect.bottom + dy, cropRect.top + MIN_CROP_SIZE));
                    cropRect.right = newR;
                    cropRect.bottom = newB;
                    notifyCropBoxChanged();
                    invalidate();
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                dragMode = DRAG_NONE;
                isPanning = false;
                notifyCropBoxChanged();
                return true;
        }

        return super.onTouchEvent(event);
    }

    private int getHitTest(float x, float y) {
        float effectiveRadius = HANDLE_RADIUS * 1.6f;
        if (isNear(x, y, cropRect.left, cropRect.top, effectiveRadius)) return DRAG_TOP_LEFT;
        if (isNear(x, y, cropRect.right, cropRect.top, effectiveRadius)) return DRAG_TOP_RIGHT;
        if (isNear(x, y, cropRect.left, cropRect.bottom, effectiveRadius)) return DRAG_BOTTOM_LEFT;
        if (isNear(x, y, cropRect.right, cropRect.bottom, effectiveRadius)) return DRAG_BOTTOM_RIGHT;
        if (cropRect.contains(x, y)) return DRAG_BODY;
        return DRAG_NONE;
    }

    private boolean isNear(float x1, float y1, float x2, float y2, float radius) {
        return Math.hypot(x1 - x2, y1 - y2) <= radius;
    }

    public org.opencv.core.Rect getSelectedCropRect() {
        if (sourceBitmap == null || bmpDstRect.width() <= 0 || bmpDstRect.height() <= 0) {
            return new org.opencv.core.Rect(0, 0, 100, 100);
        }

        float scaleX = (float) sourceBitmap.getWidth() / bmpDstRect.width();
        float scaleY = (float) sourceBitmap.getHeight() / bmpDstRect.height();

        int bx = (int) Math.max(0, (cropRect.left - bmpDstRect.left) * scaleX);
        int by = (int) Math.max(0, (cropRect.top - bmpDstRect.top) * scaleY);
        int bw = (int) Math.min(sourceBitmap.getWidth() - bx, cropRect.width() * scaleX);
        int bh = (int) Math.min(sourceBitmap.getHeight() - by, cropRect.height() * scaleY);

        return new org.opencv.core.Rect(bx, by, Math.max(4, bw), Math.max(4, bh));
    }

    @Nullable
    public Bitmap getCroppedBitmap() {
        if (sourceBitmap == null || sourceBitmap.isRecycled()) return null;
        org.opencv.core.Rect r = getSelectedCropRect();
        try {
            return Bitmap.createBitmap(sourceBitmap, r.x, r.y, r.width, r.height);
        } catch (Exception e) {
            return null;
        }
    }
}

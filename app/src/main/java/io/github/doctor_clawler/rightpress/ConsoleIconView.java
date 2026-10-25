package io.github.doctor_clawler.rightpress;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Small decorative line icons; the containing control supplies the accessible label. */
public final class ConsoleIconView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private String icon;
    private int tintColor = 0xff83a9ff;

    public ConsoleIconView(Context context) {
        this(context, "mouse");
    }

    public ConsoleIconView(Context context, String icon) {
        super(context);
        this.icon = icon;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setFocusable(false);
    }

    public void setIcon(String icon) {
        this.icon = icon;
        invalidate();
    }

    public void setTintColor(int color) {
        tintColor = color;
        invalidate();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int size = Math.round(24 * getResources().getDisplayMetrics().density);
        setMeasuredDimension(resolveSize(size, widthMeasureSpec), resolveSize(size, heightMeasureSpec));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float availableWidth = getWidth() - getPaddingLeft() - getPaddingRight();
        float availableHeight = getHeight() - getPaddingTop() - getPaddingBottom();
        float scale = Math.min(availableWidth, availableHeight) / 24f;
        if (scale <= 0 || icon == null) return;
        int saved = canvas.save();
        canvas.translate(getPaddingLeft() + (availableWidth - 24 * scale) / 2f,
                getPaddingTop() + (availableHeight - 24 * scale) / 2f);
        canvas.scale(scale, scale);
        paint.setColor(tintColor);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.6f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        path.reset();
        switch (icon) {
            case "brand":
                paint.setStrokeWidth(1.9f);
                path.moveTo(6, 19);
                path.lineTo(6, 5);
                path.lineTo(12, 5);
                path.cubicTo(18, 5, 18, 13, 12, 13);
                path.lineTo(6, 13);
                path.moveTo(12, 13);
                path.lineTo(19, 20);
                break;
            case "mouse":
            case ActionCatalog.TOGGLE_MOUSE:
                roundedRect(canvas, 5, 2, 19, 22, 7);
                line(12, 2, 12, 10);
                line(5, 10, 19, 10);
                break;
            case "keyboard":
                roundedRect(canvas, 2, 5, 22, 19, 2);
                for (int x = 6; x <= 16; x += 5) {
                    line(x, 9, x + 1, 9);
                    line(x, 13, x + 1, 13);
                }
                line(8, 16, 16, 16);
                break;
            case ActionCatalog.LONG_PRESS:
                path.moveTo(9, 12);
                path.lineTo(9, 5);
                path.cubicTo(9, 2.3f, 13, 2.3f, 13, 5);
                path.lineTo(13, 11);
                path.lineTo(15, 10);
                path.lineTo(19, 12);
                path.lineTo(19, 17);
                path.cubicTo(19, 20, 17, 22, 14, 22);
                path.lineTo(12, 22);
                path.cubicTo(10, 22, 9, 21, 8, 19);
                path.lineTo(4, 14);
                path.cubicTo(2.5f, 11.6f, 5, 10.7f, 7, 12);
                path.lineTo(9, 14);
                path.moveTo(5, 7);
                path.cubicTo(5, .6f, 13.1f, -.3f, 16, 4);
                break;
            case ActionCatalog.BACK:
                path.moveTo(10, 5);
                path.lineTo(3, 12);
                path.lineTo(10, 19);
                path.moveTo(3, 12);
                path.lineTo(15, 12);
                path.cubicTo(18.3f, 12, 21, 14.7f, 21, 18);
                break;
            case ActionCatalog.HOME:
                path.moveTo(3, 10);
                path.lineTo(12, 3);
                path.lineTo(21, 10);
                path.lineTo(21, 21);
                path.lineTo(15, 21);
                path.lineTo(15, 14);
                path.lineTo(9, 14);
                path.lineTo(9, 21);
                path.lineTo(3, 21);
                path.close();
                break;
            case ActionCatalog.RECENTS:
                roundedRect(canvas, 4, 6, 17, 21, 2);
                path.moveTo(8, 3);
                path.lineTo(19, 3);
                path.quadTo(21, 3, 21, 5);
                path.lineTo(21, 17);
                break;
            case ActionCatalog.NOTIFICATIONS:
                path.moveTo(5, 9);
                path.cubicTo(5, -.3f, 19, -.3f, 19, 9);
                path.lineTo(19, 15);
                path.lineTo(21, 18);
                path.lineTo(3, 18);
                path.lineTo(5, 15);
                path.close();
                line(9, 21, 15, 21);
                break;
            case ActionCatalog.QUICK_SETTINGS:
                line(3, 6, 21, 6);
                line(3, 12, 21, 12);
                line(3, 18, 21, 18);
                roundedRect(canvas, 7, 3.5f, 10, 8.5f, 1);
                roundedRect(canvas, 15, 9.5f, 18, 14.5f, 1);
                roundedRect(canvas, 6, 15.5f, 9, 20.5f, 1);
                break;
            case ActionCatalog.LAUNCH_APP:
                roundedRect(canvas, 3, 3, 10, 10, 1.5f);
                roundedRect(canvas, 14, 3, 21, 10, 1.5f);
                roundedRect(canvas, 3, 14, 10, 21, 1.5f);
                roundedRect(canvas, 14, 14, 21, 21, 1.5f);
                break;
            case "arrow":
                path.moveTo(9, 5);
                path.lineTo(16, 12);
                path.lineTo(9, 19);
                break;
            case "help":
                canvas.drawCircle(12, 12, 9, paint);
                path.moveTo(9, 9);
                path.cubicTo(9, 5, 16, 5, 16, 9);
                path.cubicTo(16, 11, 12, 11, 12, 14);
                line(12, 17, 12, 17.1f);
                break;
            default:
                break;
        }
        canvas.drawPath(path, paint);
        canvas.restoreToCount(saved);
    }

    private void roundedRect(Canvas canvas, float left, float top, float right, float bottom, float radius) {
        rect.set(left, top, right, bottom);
        canvas.drawRoundRect(rect, radius, radius, paint);
    }

    private void line(float x1, float y1, float x2, float y2) {
        path.moveTo(x1, y1);
        path.lineTo(x2, y2);
    }
}

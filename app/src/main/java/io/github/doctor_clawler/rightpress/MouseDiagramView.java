package io.github.doctor_clawler.rightpress;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;

/** Resolution-independent rendering of the Console design's mouse selector. */
public final class MouseDiagramView extends View {
    private static final float DRAWING_WIDTH = 400f;
    private static final float DRAWING_HEIGHT = 460f;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path shell = new Path();
    private final Path leftButton = new Path();
    private final Path rightButton = new Path();
    private final Path palm = new Path();
    private final RectF bounds = new RectF();
    private final Shader shellFinish = new LinearGradient(82, 28, 318, 304,
            new int[]{0xff8a929e, 0xff505761, 0xff282d35, 0xff444b55},
            new float[]{0f, .2f, .65f, 1f}, Shader.TileMode.CLAMP);
    private final Shader leftFinish = new LinearGradient(90, 36, 175, 217,
            0xff5c6571, 0xff343a44, Shader.TileMode.CLAMP);
    private final Shader rightFinish = new LinearGradient(204, 36, 278, 217,
            new int[]{0xff78a4ff, 0xff4380ff, 0xff245dd8},
            new float[]{0f, .5f, 1f}, Shader.TileMode.CLAMP);
    private final Shader palmFinish = new LinearGradient(112, 219, 310, 413,
            new int[]{0xff434b56, 0xff292f37, 0xff1b2027},
            new float[]{0f, .6f, 1f}, Shader.TileMode.CLAMP);
    private boolean active = true;

    public MouseDiagramView(Context context) {
        super(context);
        setContentDescription("우클릭 동작 변경");
        setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        createPaths();
    }

    public void setActive(boolean active) {
        if (this.active == active) return;
        this.active = active;
        invalidate();
    }

    private void createPaths() {
        shell.moveTo(200, 28);
        shell.cubicTo(126, 28, 82, 80, 82, 159);
        shell.lineTo(82, 282);
        shell.cubicTo(82, 367, 126, 422, 200, 422);
        shell.cubicTo(274, 422, 318, 367, 318, 282);
        shell.lineTo(318, 159);
        shell.cubicTo(318, 80, 274, 28, 200, 28);
        shell.close();

        leftButton.moveTo(196, 36);
        leftButton.cubicTo(130, 38, 90, 85, 90, 159);
        leftButton.lineTo(90, 207);
        leftButton.cubicTo(127, 215, 161, 217, 196, 215);
        leftButton.close();

        rightButton.moveTo(204, 36);
        rightButton.cubicTo(270, 38, 310, 85, 310, 159);
        rightButton.lineTo(310, 207);
        rightButton.cubicTo(273, 215, 239, 217, 204, 215);
        rightButton.close();

        palm.moveTo(90, 219);
        palm.cubicTo(164, 233, 236, 233, 310, 219);
        palm.lineTo(310, 282);
        palm.cubicTo(310, 362, 269, 413, 200, 413);
        palm.cubicTo(131, 413, 90, 362, 90, 282);
        palm.close();
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredWidth = Math.round(320 * getResources().getDisplayMetrics().density);
        int width = resolveSize(desiredWidth + getPaddingLeft() + getPaddingRight(), widthMeasureSpec);
        int contentWidth = Math.max(0, width - getPaddingLeft() - getPaddingRight());
        int desiredHeight = Math.round(contentWidth * DRAWING_HEIGHT / DRAWING_WIDTH)
                + getPaddingTop() + getPaddingBottom();
        int height = resolveSize(desiredHeight, heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float availableWidth = getWidth() - getPaddingLeft() - getPaddingRight();
        float availableHeight = getHeight() - getPaddingTop() - getPaddingBottom();
        float scale = Math.min(availableWidth / DRAWING_WIDTH, availableHeight / DRAWING_HEIGHT);
        if (scale <= 0) return;
        int saved = canvas.save();
        canvas.translate(getPaddingLeft() + (availableWidth - DRAWING_WIDTH * scale) / 2f,
                getPaddingTop() + (availableHeight - DRAWING_HEIGHT * scale) / 2f);
        canvas.scale(scale, scale);

        fill(0x99080b10);
        bounds.set(104, 408, 302, 430);
        canvas.drawOval(bounds, paint);
        drawSurface(canvas, shell, shellFinish, 0xff737e8e, 1.3f);
        drawSurface(canvas, leftButton, leftFinish, 0xff697583, .8f);
        if (active) {
            drawSurface(canvas, rightButton, rightFinish, 0xff8aafff, 1.5f);
        } else {
            fill(0xff424a5a);
            canvas.drawPath(rightButton, paint);
            stroke(0xff788396, 1.5f);
            canvas.drawPath(rightButton, paint);
        }
        drawSurface(canvas, palm, palmFinish, 0xff66717f, .8f);

        stroke(0xff121820, 3f);
        canvas.drawLine(200, 35, 200, 58, paint);
        canvas.drawLine(200, 150, 200, 215, paint);
        bounds.set(185, 67, 215, 143);
        fill(0xff151b23);
        canvas.drawRoundRect(bounds, 15, 15, paint);
        stroke(0xff95a2b5, 1.2f);
        canvas.drawRoundRect(bounds, 15, 15, paint);
        bounds.set(192, 75, 208, 133);
        fill(0xff8994a3);
        canvas.drawRoundRect(bounds, 8, 8, paint);
        stroke(0xff343f4d, 2);
        for (int y = 85; y <= 125; y += 8) canvas.drawLine(194, y, 206, y, paint);

        fill(0xff8a96a6);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        paint.setTextSize(26);
        paint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("RP", 200, 340, paint);
        fill(active ? 0xffdbe7ff : 0xffa3aab5);
        canvas.drawCircle(275, 136, 4, paint);

        if (isPressed() || isFocused()) {
            stroke(0xff83a9ff, isFocused() ? 3f : 2f);
            bounds.set(72, 18, 328, 432);
            canvas.drawRoundRect(bounds, 120, 120, paint);
        }
        canvas.restoreToCount(saved);
    }

    private void drawSurface(Canvas canvas, Path path, Shader shader, int outline, float width) {
        fill(Color.WHITE);
        paint.setShader(shader);
        canvas.drawPath(path, paint);
        stroke(outline, width);
        canvas.drawPath(path, paint);
    }

    private void fill(int color) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
    }

    private void stroke(int color, float width) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(width);
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(color);
    }

    @Override protected void drawableStateChanged() {
        super.drawableStateChanged();
        invalidate();
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        if (isClickable()) info.setClassName(Button.class.getName());
    }
}

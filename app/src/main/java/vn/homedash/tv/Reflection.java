package vn.homedash.tv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/** The view above it drawn upside down and fading out, like a glossy floor. Redrawn when the target changes. */
public class Reflection extends View {
    private View target;
    private final Paint fade = new Paint();

    public Reflection(Context context, AttributeSet attrs) {
        super(context, attrs);
        fade.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
    }

    void setTarget(View v) {
        target = v;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        fade.setShader(new LinearGradient(0, 0, 0, h, 0x59000000, 0x00000000, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (target == null || target.getWidth() == 0) return;
        int save = canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
        canvas.save();
        canvas.scale(1, -1);
        canvas.translate(0, -target.getHeight());
        target.draw(canvas);
        canvas.restore();
        canvas.drawRect(0, 0, getWidth(), getHeight(), fade);
        canvas.restoreToCount(save);
    }
}

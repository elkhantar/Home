package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ComposeShader;
import android.graphics.PorterDuff;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * "Liquid glass" for pills, cards and buttons: the blurred backdrop behind the view, a light tint, a
 * soft sheen on the upper half and a thin even edge. Without a backdrop (no photo or
 * wallpaper) it falls back to the plain translucent pill the launcher always had.
 */
final class GlassDrawable extends Drawable {
    private final Renderer renderer;
    private final View owner;
    private final Backdrop backdrop;
    private final float radius, density;
    /** Extra darkening over the baked-in veil: none for buttons and cards, some for menus full of text. */
    private final int veil;
    private final RectF rect = new RectF();
    private final int[] loc = new int[2];

    GlassDrawable(View owner, Backdrop backdrop, float radiusPx, float density) {
        this(owner, backdrop, radiusPx, density, 0);
    }

    GlassDrawable(View owner, Backdrop backdrop, float radiusPx, float density, int veil) {
        this.renderer = new Renderer(backdrop);
        this.veil = veil;
        this.owner = owner;
        this.backdrop = backdrop;
        this.radius = radiusPx;
        this.density = density;
        backdrop.register(owner);
    }

    @Override
    public void draw(Canvas c) {
        Rect b = getBounds();
        rect.set(b);
        owner.getLocationOnScreen(loc);
        renderer.draw(c, rect, Math.min(radius, rect.height() / 2f), loc[0] - b.left, loc[1] - b.top, density, veil, EDGE, 255);
    }

    /**
     * Glass in rect (canvas coordinates). screenX/screenY: where the canvas origin sits on the screen,
     * so the right part of the backdrop shows through.
     */
    /** The pills' and panels' edge before the glass: an even 1dp line, 22% white. */
    static final int EDGE = 0x38FFFFFF;
    /** The dock band's edge before the glass: an even 1dp line, 12% white. */
    static final int DOCK_EDGE = 0x1FFFFFFF;

    /** Per-surface drawing state; never shared between independently positioned glass panels. */
    static final class Renderer {
        private final Backdrop backdrop;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Matrix matrix = new Matrix();
        private final RectF edge = new RectF();
        private Shader body;
        private BitmapShader photo;
        private float top = Float.NaN, bottom = Float.NaN;

        Renderer(Backdrop backdrop) {
            this.backdrop = backdrop;
            backdrop.register(this);
        }

        // Wallpaper changes (including stop) release cached shader/bitmap references immediately.
        void clear() {
            body = null;
            photo = null;
            paint.setShader(null);
            top = bottom = Float.NaN;
        }

        void draw(Canvas c, RectF rect, float radius, float screenX, float screenY,
                  float density, int veil, int edgeColor, int alpha) {
            Bitmap bmp = backdrop.bitmap();
            if (body == null || top != rect.top || bottom != rect.bottom) {
                top = rect.top;
                bottom = rect.bottom;
                Shader sheen = new LinearGradient(0, rect.top, 0, rect.top + rect.height() * 0.6f,
                        0x26FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
                if (bmp != null && backdrop.screenWidth() > 0) {
                    photo = new BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                    body = new ComposeShader(photo, sheen, PorterDuff.Mode.SRC_OVER);
                } else {
                    photo = null;
                    body = new ComposeShader(new LinearGradient(0, rect.top, 0, rect.bottom,
                            0x2EFFFFFF, 0x14FFFFFF, Shader.TileMode.CLAMP), sheen, PorterDuff.Mode.SRC_OVER);
                }
            }
            if (photo != null) {
                matrix.setScale(backdrop.screenWidth() / (float) bmp.getWidth(),
                        backdrop.screenHeight() / (float) bmp.getHeight());
                matrix.postTranslate(-screenX, -screenY);
                photo.setLocalMatrix(matrix);
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setFilterBitmap(photo != null);
            paint.setShader(body);
            paint.setAlpha(alpha);
            c.drawRoundRect(rect, radius, radius, paint);
            if (veil != 0 && bmp != null) {
                paint.setShader(null);
                paint.setColor(veil);
                c.drawRoundRect(rect, radius, radius, paint);
            }
            paint.setShader(null);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1f, density));
            paint.setColor(edgeColor);
            edge.set(rect);
            edge.inset(Math.max(1f, density) / 2f, Math.max(1f, density) / 2f);
            c.drawRoundRect(edge, radius, radius, paint);
        }
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}

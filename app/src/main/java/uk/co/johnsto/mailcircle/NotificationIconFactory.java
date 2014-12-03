package uk.co.johnsto.mailcircle;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * NotificationIconFactory produces the notification icon that appears on the lockscreen in
 * Android 5.
 */
public class NotificationIconFactory {
    public static enum Style {
        DISC("disc"), // a solid colour
        PIE("pie"), // a pie chart
        RING("ring"); // center solid, edge coloured like pie

        public String name;

        Style(String name) {
            this.name = name;
        }

        public static Style fromName(String name) {
            if (name != null) {
                for (Style style : Style.values()) {
                    if (style.name.equals(name)) {
                        return style;
                    }
                }
            }
            return null;
        }
    }

    public static Style DEFAULT_STYLE = Style.PIE;

    private final BitmapDrawable mDrawable;
    private int mColor = Color.BLACK;
    private int mTextColor = Color.WHITE;
    private int mNumber;
    private List<Slice> mSlices;
    private Style mStyle = DEFAULT_STYLE;

    /**
     * Creates a new factory
     *
     * @param res    Resources (used to get correct dimensions)
     * @param bitmap Base bitmap
     */
    public NotificationIconFactory(Resources res, Bitmap bitmap) {
        mDrawable = new BitmapDrawable(res, bitmap);
    }

    /**
     * Sets the style of the icon.
     *
     * @param style Style to use
     * @return The factory
     */
    public NotificationIconFactory setStyle(Style style) {
        mStyle = style;
        return this;
    }

    /**
     * Sets the style of the icon.
     *
     * @param styleName Name of the style
     * @return The factory
     */
    public NotificationIconFactory setStyle(String styleName) {
        mStyle = Style.fromName(styleName);
        return this;
    }

    /**
     * Set the color of the circle.
     *
     * @param color Circle color
     * @return The factory
     */
    public NotificationIconFactory setColor(int color) {
        mColor = color;
        return this;
    }

    /**
     * Adds a pie slice to the circle.
     *
     * @param count size of the slice
     * @param color Color of the slice
     * @return The factory
     */
    public NotificationIconFactory addSlice(int count, int color) {
        if (mSlices == null) {
            mSlices = new ArrayList<Slice>();
        }
        mSlices.add(new Slice(count, color));
        return this;
    }

    /**
     * Calculates the sum of all the slice counts
     *
     * @return total count
     */
    private int totalSliceCount() {
        if (mSlices == null || mSlices.size() == 0) {
            return 0;
        }
        Iterator<Slice> it = mSlices.iterator();
        int count = 0;
        while (it.hasNext()) {
            Slice slice = it.next();
            count += slice.count;
        }
        return count;
    }

    /**
     * Set the number to display in the middle of the circle.
     *
     * @param number number to display
     * @return The original factory
     */
    public NotificationIconFactory setNumber(int number) {
        mNumber = number;
        return this;
    }

    /**
     * Draws the circle to the bitmap and returns the drawable.
     *
     * @return a drawable containing the rendered icon
     */
    public BitmapDrawable draw() {
        final Bitmap bitmap = mDrawable.getBitmap();
        final Canvas canvas = new Canvas(bitmap);
        final int width = bitmap.getWidth();
        final int height = bitmap.getHeight();

        mDrawable.setAntiAlias(true);

        switch (mStyle) {
            case DISC:
                drawDisc(canvas);
                break;
            case PIE:
                drawPie(canvas);
                break;
            case RING:
                drawRing(canvas);
                break;
        }

        // Draw text in dead centre
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.HINTING_ON);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setColor(mTextColor);
        textPaint.setTextSize(height / 2); // guesstimate good font size

        String text = String.format("%,d", mNumber);
        Rect bounds = new Rect();
        textPaint.getTextBounds(text, 0, text.length(), bounds);
        canvas.drawText(text, width / 2, height / 2 + bounds.height() / 2, textPaint);

        mDrawable.draw(canvas);

        return mDrawable;
    }

    private void drawDisc(Canvas canvas) {
        final int width = canvas.getWidth();
        final int height = canvas.getHeight();

        Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        circlePaint.setColor(mColor);
        canvas.drawCircle(width / 2, height / 2, width / 2, circlePaint);
    }

    private void drawPie(Canvas canvas) {
        final int width = canvas.getWidth();
        final int height = canvas.getHeight();

        // Draw slices starting from 12 o'clock
        float startAngle = -90;
        float total = totalSliceCount();
        for (Slice slice : mSlices) {
            Paint slicePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            slicePaint.setColor(slice.color);
            float sweepAngle = (slice.count / total) * 360;
            canvas.drawArc(0, 0, width, height, startAngle, sweepAngle, true, slicePaint);
            startAngle += sweepAngle;
        }
    }

    private void drawRing(Canvas canvas) {
        final int width = canvas.getWidth();
        final int height = canvas.getHeight();
        final int radius = width / 3;

        drawPie(canvas);
        Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        circlePaint.setColor(mColor);
        canvas.drawCircle(width / 2, height / 2, radius, circlePaint);
    }

    /**
     * Draws the icon and returns a Bitmap.
     *
     * @return Rendered bitmap
     */
    public Bitmap build() {
        BitmapDrawable drawable = draw();
        return drawable.getBitmap();
    }

    private static class Slice {
        int count;
        int color;

        public Slice(int count, int color) {
            this.count = count;
            this.color = color;
        }
    }
}

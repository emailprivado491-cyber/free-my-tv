package io.freemytv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Movie;
import android.graphics.Paint;
import android.graphics.RectF;
import java.io.IOException;
import java.io.InputStream;

/**
 * Optional animated background loaded from app/assets. GIF is used deliberately: Movie is available on the old
 * Android TV versions supported by this project, while keeping the normal background as a safe fallback.
 */
final class AnimatedBanner {
    private final Movie movie;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final long startedAt = android.os.SystemClock.uptimeMillis();
    private final int duration;

    private AnimatedBanner(Movie movie) {
        this.movie = movie;
        duration = Math.max(1, movie.duration());
    }

    void setColorFilter(ColorFilter filter) {
        paint.setColorFilter(filter);
    }

    static AnimatedBanner load(Context context, String assetName) {
        if (assetName == null || assetName.trim().isEmpty()) return null;
        String name = assetName.trim();
        if (!name.toLowerCase(java.util.Locale.ROOT).endsWith(".gif")) {
            AppLog.w("animated banner needs a GIF asset: " + name);
            return null;
        }
        try (InputStream in = context.getAssets().open(name)) {
            Movie movie = Movie.decodeStream(in);
            if (movie == null || movie.width() <= 0 || movie.height() <= 0) {
                AppLog.w("animated banner is unreadable: " + name);
                return null;
            }
            return new AnimatedBanner(movie);
        } catch (IOException | RuntimeException e) {
            AppLog.w("animated banner unavailable: " + name + " (" + e.getMessage() + ")");
            return null;
        }
    }

    /** Draws a center-cropped frame into the screen rectangle. Returns true when a frame was drawn. */
    boolean draw(Canvas canvas, RectF screen, long now) {
        int time = (int) ((now - startedAt) % duration);
        movie.setTime(time);
        float scale = Math.max(screen.width() / movie.width(), screen.height() / movie.height());
        canvas.save();
        canvas.clipRect(screen);
        canvas.scale(scale, scale, screen.centerX(), screen.centerY());
        movie.draw(canvas, screen.centerX() - movie.width() / 2, screen.centerY() - movie.height() / 2, paint);
        canvas.restore();
        return true;
    }
}

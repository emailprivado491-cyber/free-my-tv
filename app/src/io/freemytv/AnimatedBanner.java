package io.freemytv;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Movie;
import android.graphics.Paint;
import android.graphics.RectF;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Optional animated background loaded from app/assets. GIF is used deliberately: Movie is available on the old
 * Android TV versions supported by this project, while keeping the normal background as a safe fallback.
 */
final class AnimatedBanner {
    private static final int MAX_BYTES = 8 * 1024 * 1024;
    private static final int MAX_WIDTH = 1920, MAX_HEIGHT = 1080;
    private static final int MAX_DURATION_MS = 10_000;
    private static final long MIN_FRAME_MS = 33; // cap visual updates at about 30 fps
    private final Movie movie;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private final long startedAt = android.os.SystemClock.uptimeMillis();
    private final int duration;
    private long lastDrawAt = Long.MIN_VALUE;

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
        try (InputStream raw = context.getAssets().open(name);
             BufferedInputStream in = new BufferedInputStream(raw)) {
            int size = in.available();
            if (size > MAX_BYTES) {
                AppLog.w("animated banner is larger than 8 MB: " + name);
                return null;
            }
            int[] gifSize = gifSize(in);
            if (gifSize == null || gifSize[0] <= 0 || gifSize[1] <= 0
                    || gifSize[0] > MAX_WIDTH || gifSize[1] > MAX_HEIGHT) {
                AppLog.w("animated banner resolution exceeds 1920x1080: " + name);
                return null;
            }
            Movie movie = Movie.decodeStream(in);
            if (movie == null || movie.width() <= 0 || movie.height() <= 0) {
                AppLog.w("animated banner is unreadable: " + name);
                return null;
            }
            if (movie.duration() > MAX_DURATION_MS) {
                AppLog.w("animated banner is longer than 10 seconds: " + name);
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
        if (lastDrawAt != Long.MIN_VALUE && now - lastDrawAt < MIN_FRAME_MS) return false;
        lastDrawAt = now;
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

    /** Reads the logical screen size from the GIF header without decoding all frames. */
    private static int[] gifSize(BufferedInputStream in) throws IOException {
        in.mark(16);
        byte[] header = new byte[10];
        int read = 0;
        while (read < header.length) {
            int n = in.read(header, read, header.length - read);
            if (n < 0) break;
            read += n;
        }
        in.reset();
        if (read < 10 || header[0] != 'G' || header[1] != 'I' || header[2] != 'F') return null;
        int width = (header[6] & 255) | ((header[7] & 255) << 8);
        int height = (header[8] & 255) | ((header[9] & 255) << 8);
        return new int[] {width, height};
    }
}

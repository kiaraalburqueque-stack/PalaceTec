package com.tecsup.palacetec.media;

import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;

/**
 * Píxeles ARGB por filas, convertidos como Pillow. Java aclara los grises al pasarlos a RGB, porque los considera
 * lineales, y no sabe leer JPEG en CMYK.
 */
final class PixelRows {

    private interface RowReader {
        void read(int y, int[] argb);
    }

    private final int width;
    private final int height;
    private final boolean alpha;
    private final RowReader rows;

    private PixelRows(int width, int height, boolean alpha, RowReader rows) {
        this.width = width;
        this.height = height;
        this.alpha = alpha;
        this.rows = rows;
    }

    static PixelRows of(BufferedImage image) {
        ColorModel colors = image.getColorModel();
        boolean palette = colors instanceof IndexColorModel;
        // Como en Django: en una imagen con paleta se ignora la transparencia.
        boolean alpha = colors.hasAlpha() && !palette;
        boolean gray = !palette && colors.getColorSpace().getType() == ColorSpace.TYPE_GRAY;
        return new PixelRows(image.getWidth(), image.getHeight(), alpha, gray ? gray(image, alpha)
                : (y, argb) -> image.getRGB(0, y, image.getWidth(), 1, argb, 0, image.getWidth()));
    }

    /** Canales de un JPEG en CMYK, que por convención de Adobe se guardan invertidos. */
    static PixelRows ofCmyk(Raster raster, boolean ycck) {
        int[] samples = new int[raster.getWidth() * 4];
        return new PixelRows(raster.getWidth(), raster.getHeight(), false, (y, argb) -> {
            raster.getPixels(raster.getMinX(), raster.getMinY() + y, raster.getWidth(), 1, samples);
            for (int x = 0; x < raster.getWidth(); x++) {
                int i = 4 * x;
                int black = samples[i + 3];
                int[] ink = ycck ? fromYcc(samples[i], samples[i + 1], samples[i + 2])
                        : new int[] {samples[i], samples[i + 1], samples[i + 2]};
                argb[x] = 0xFF000000 | ink[0] * black / 255 << 16 | ink[1] * black / 255 << 8 | ink[2] * black / 255;
            }
        });
    }

    private static int[] fromYcc(int luma, int blue, int red) {
        return new int[] {255 - clamp(luma + 1.402 * (red - 128)),
                255 - clamp(luma - 0.344136 * (blue - 128) - 0.714136 * (red - 128)),
                255 - clamp(luma + 1.772 * (blue - 128))};
    }

    private static RowReader gray(BufferedImage image, boolean alpha) {
        Raster raster = image.getRaster();
        int bands = raster.getNumBands();
        int max = (1 << image.getColorModel().getComponentSize(0)) - 1;
        int[] samples = new int[image.getWidth() * bands];
        return (y, argb) -> {
            raster.getPixels(0, y, image.getWidth(), 1, samples);
            for (int x = 0; x < image.getWidth(); x++) {
                int value = (int) (samples[x * bands] * 255L / max);
                int opacity = alpha ? (int) (samples[x * bands + 1] * 255L / max) : 255;
                argb[x] = opacity << 24 | value << 16 | value << 8 | value;
            }
        };
    }

    static int clamp(double value) {
        return (int) Math.max(0, Math.min(255, Math.round(value)));
    }

    int width() {
        return width;
    }

    int height() {
        return height;
    }

    boolean hasAlpha() {
        return alpha;
    }

    void read(int y, int[] argb) {
        rows.read(y, argb);
    }
}

package com.tecsup.palacetec.media;

import java.awt.image.BufferedImage;

/** ImageOps.fit de Pillow: cuadrado central redimensionado con el filtro Lanczos. */
final class SquareResampler {

    private static final int CHANNELS = 4;
    private static final double LOBES = 3;

    private SquareResampler() {
    }

    /** Primer píxel de origen y peso de cada uno, para un píxel del resultado. */
    private record Kernel(int start, double[] weights) {
    }

    static BufferedImage fit(PixelRows source, int size) {
        int side = Math.min(source.width(), source.height());
        Kernel[] columns = kernels(source.width(), (source.width() - side) / 2.0, side, size);
        Kernel[] rows = kernels(source.height(), (source.height() - side) / 2.0, side, size);
        int firstRow = rows[0].start();
        int lastRow = rows[size - 1].start() + rows[size - 1].weights().length;
        float[][] resized = new float[lastRow - firstRow][];
        int[] argb = new int[source.width()];
        for (int y = firstRow; y < lastRow; y++) {
            source.read(y, argb);
            resized[y - firstRow] = resizeRow(argb, source.hasAlpha(), columns);
        }
        BufferedImage result = new BufferedImage(size, size, source.hasAlpha() ? BufferedImage.TYPE_INT_ARGB
                : BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                result.setRGB(x, y, pixel(resized, rows[y], firstRow, x, source.hasAlpha()));
            }
        }
        return result;
    }

    private static Kernel[] kernels(int length, double offset, int side, int size) {
        double scale = (double) side / size;
        double stretch = Math.max(1, scale);
        Kernel[] kernels = new Kernel[size];
        for (int i = 0; i < size; i++) {
            double center = offset + (i + 0.5) * scale;
            int start = Math.max(0, (int) (center - LOBES * stretch + 0.5));
            int end = Math.min(length, (int) (center + LOBES * stretch + 0.5));
            double[] weights = new double[end - start];
            double total = 0;
            for (int j = start; j < end; j++) {
                weights[j - start] = lanczos((j + 0.5 - center) / stretch);
                total += weights[j - start];
            }
            for (int j = 0; j < weights.length; j++) {
                weights[j] /= total;
            }
            kernels[i] = new Kernel(start, weights);
        }
        return kernels;
    }

    private static double lanczos(double x) {
        return Math.abs(x) < LOBES ? sinc(x) * sinc(x / LOBES) : 0;
    }

    private static double sinc(double x) {
        return x == 0 ? 1 : Math.sin(Math.PI * x) / (Math.PI * x);
    }

    /** Con transparencia se mezclan colores premultiplicados, para que los píxeles invisibles no ensucien el borde. */
    private static float[] resizeRow(int[] argb, boolean alpha, Kernel[] columns) {
        float[] row = new float[columns.length * CHANNELS];
        for (int x = 0; x < columns.length; x++) {
            Kernel kernel = columns[x];
            double[] sum = new double[CHANNELS];
            for (int j = 0; j < kernel.weights().length; j++) {
                int color = argb[kernel.start() + j];
                double weight = kernel.weights()[j];
                double opacity = alpha ? (color >>> 24) / 255.0 : 1;
                sum[0] += weight * opacity * (color >> 16 & 0xFF);
                sum[1] += weight * opacity * (color >> 8 & 0xFF);
                sum[2] += weight * opacity * (color & 0xFF);
                sum[3] += weight * opacity * 255;
            }
            for (int channel = 0; channel < CHANNELS; channel++) {
                row[x * CHANNELS + channel] = (float) sum[channel];
            }
        }
        return row;
    }

    private static int pixel(float[][] resized, Kernel kernel, int firstRow, int x, boolean alpha) {
        double[] sum = new double[CHANNELS];
        for (int j = 0; j < kernel.weights().length; j++) {
            float[] row = resized[kernel.start() - firstRow + j];
            for (int channel = 0; channel < CHANNELS; channel++) {
                sum[channel] += kernel.weights()[j] * row[x * CHANNELS + channel];
            }
        }
        int opacity = alpha ? PixelRows.clamp(sum[3]) : 255;
        if (opacity == 0) {
            return 0;
        }
        double restore = alpha ? 255.0 / opacity : 1;
        return opacity << 24 | PixelRows.clamp(sum[0] * restore) << 16 | PixelRows.clamp(sum[1] * restore) << 8
                | PixelRows.clamp(sum[2] * restore);
    }
}

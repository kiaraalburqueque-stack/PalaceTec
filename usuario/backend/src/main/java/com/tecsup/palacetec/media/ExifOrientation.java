package com.tecsup.palacetec.media;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Orientación con la que la cámara guardó la foto (etiqueta EXIF 0x0112): de 1, sin cambios, a 8. */
final class ExifOrientation {

    static final int NORMAL = 1;

    private static final int TAG = 0x0112;
    private static final int TIFF_MAGIC = 42;
    private static final int ENTRY_SIZE = 12;
    private static final String EXIF_HEADER = "Exif";
    private static final int EXIF_HEADER_LENGTH = 6;

    private ExifOrientation() {
    }

    /** Los metadatos ausentes o dañados se ignoran. */
    static int read(byte[] content, ImageKind kind) {
        try {
            ByteBuffer tiff = switch (kind) {
                case JPEG -> jpegExif(content);
                case PNG -> pngExif(content);
                case WEBP -> webpExif(content);
            };
            return tiff == null ? NORMAL : orientation(tiff);
        } catch (IndexOutOfBoundsException | BufferUnderflowException | IllegalArgumentException exception) {
            return NORMAL;
        }
    }

    private static ByteBuffer jpegExif(byte[] content) {
        ByteBuffer data = ByteBuffer.wrap(content);
        int position = 2;
        while (position + 4 <= content.length && (data.get(position) & 0xFF) == 0xFF) {
            int marker = data.get(position + 1) & 0xFF;
            int length = data.getShort(position + 2) & 0xFFFF;
            if (marker == 0xDA) {
                return null; // Empiezan los píxeles.
            }
            if (marker == 0xE1 && ImageKind.hasText(content, position + 4, EXIF_HEADER)) {
                return data.slice(position + 4 + EXIF_HEADER_LENGTH, length - 2 - EXIF_HEADER_LENGTH);
            }
            position += 2 + length;
        }
        return null;
    }

    private static ByteBuffer pngExif(byte[] content) {
        ByteBuffer data = ByteBuffer.wrap(content);
        int position = 8;
        while (position + 8 <= content.length) {
            int length = data.getInt(position);
            if (ImageKind.hasText(content, position + 4, "eXIf")) {
                return data.slice(position + 8, length);
            }
            if (length < 0) {
                return null;
            }
            position = Math.addExact(position, Math.addExact(length, ENTRY_SIZE));
        }
        return null;
    }

    private static ByteBuffer webpExif(byte[] content) {
        ByteBuffer data = ByteBuffer.wrap(content).order(ByteOrder.LITTLE_ENDIAN);
        int position = 12;
        while (position + 8 <= content.length) {
            int length = data.getInt(position + 4);
            if (ImageKind.hasText(content, position, "EXIF")) {
                int header = ImageKind.hasText(content, position + 8, EXIF_HEADER) ? EXIF_HEADER_LENGTH : 0;
                return data.slice(position + 8 + header, length - header);
            }
            if (length < 0) {
                return null;
            }
            position = Math.addExact(position, Math.addExact(8, length + (length & 1)));
        }
        return null;
    }

    private static int orientation(ByteBuffer tiff) {
        tiff.order(tiff.get(0) == 'I' ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
        if (tiff.getShort(2) != TIFF_MAGIC) {
            return NORMAL;
        }
        int directory = tiff.getInt(4);
        int entries = tiff.getShort(directory) & 0xFFFF;
        for (int i = 0; i < entries; i++) {
            int entry = directory + 2 + i * ENTRY_SIZE;
            if ((tiff.getShort(entry) & 0xFFFF) == TAG) {
                int value = tiff.getShort(entry + 8) & 0xFFFF;
                return value >= NORMAL && value <= 8 ? value : NORMAL;
            }
        }
        return NORMAL;
    }
}

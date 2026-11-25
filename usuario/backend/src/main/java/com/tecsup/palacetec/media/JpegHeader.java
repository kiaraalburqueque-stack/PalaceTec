package com.tecsup.palacetec.media;

import java.nio.ByteBuffer;

/**
 * Lo que hace falta de la cabecera de un JPEG y ImageIO no informa.
 *
 * @param components canales por píxel: 4 en una imagen CMYK
 * @param ycck CMYK con los tres primeros canales en YCbCr
 * @param exif bloque TIFF con los metadatos de la cámara, o null
 */
record JpegHeader(int components, boolean ycck, ByteBuffer exif) {

    private static final int START_OF_SCAN = 0xDA;
    private static final int APP1 = 0xE1;
    private static final int APP14 = 0xEE;
    private static final String EXIF = "Exif";
    private static final int EXIF_PREFIX = 6;
    private static final int ADOBE_TRANSFORM = 11;
    private static final int YCCK = 2;

    /** Los datos dañados se ignoran: ImageIO dirá si la imagen se puede leer. */
    static JpegHeader read(byte[] content) {
        ByteBuffer data = ByteBuffer.wrap(content);
        int components = 0;
        boolean ycck = false;
        ByteBuffer exif = null;
        int position = 2;
        try {
            while (position + 4 <= content.length && unsigned(data, position) == 0xFF) {
                int marker = unsigned(data, position + 1);
                int length = data.getShort(position + 2) & 0xFFFF;
                int body = position + 4;
                if (marker == START_OF_SCAN) {
                    break;
                }
                if (isFrame(marker)) {
                    components = unsigned(data, body + 5);
                } else if (marker == APP14 && ImageKind.hasText(content, body, "Adobe")) {
                    ycck = unsigned(data, body + ADOBE_TRANSFORM) == YCCK;
                } else if (marker == APP1 && exif == null && ImageKind.hasText(content, body, EXIF)) {
                    exif = data.slice(body + EXIF_PREFIX, length - 2 - EXIF_PREFIX);
                }
                position += 2 + length;
            }
        } catch (IndexOutOfBoundsException exception) {
            return new JpegHeader(components, ycck, exif);
        }
        return new JpegHeader(components, ycck, exif);
    }

    /** SOF0 a SOF15, menos las tablas DHT, JPG y DAC que comparten el rango. */
    private static boolean isFrame(int marker) {
        return marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC;
    }

    private static int unsigned(ByteBuffer data, int position) {
        return data.get(position) & 0xFF;
    }
}

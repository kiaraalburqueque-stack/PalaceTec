package com.tecsup.palacetec.media;

import com.tecsup.palacetec.exception.InvalidFieldsException;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

/** Foto de avatar (HU65): valida el archivo, lo recorta al centro a 300 x 300 y lo guarda como WEBP. */
@Component
public class AvatarImages {

    public static final String TOO_BIG = "La foto no debe superar 2 MB.";

    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final int SIZE = 300;
    private static final float QUALITY = 0.85f;
    private static final int CMYK_COMPONENTS = 4;
    /** Evita "bombas de descompresión" dentro de un archivo pequeño. */
    private static final long MAX_PIXELS = 25_000_000;
    /** A partir de aquí Pillow ni siquiera abre la imagen. */
    private static final long BOMB_PIXELS = 178_956_970;

    private static final String BAD_FORMAT = "La foto debe ser JPG, PNG o WEBP.";
    private static final String BAD_CONTENT = "El contenido del archivo no corresponde a una imagen válida.";
    private static final String TOO_MANY_PIXELS = "La imagen tiene demasiados píxeles.";
    private static final String NOT_PROCESSED = "No se pudo procesar la imagen.";

    public byte[] toAvatar(MultipartFile upload) throws IOException {
        if (!ImageKind.isAllowedMime(upload.getContentType())) {
            throw invalid(BAD_FORMAT);
        }
        ImageKind kind = ImageKind.ofFile(upload.getOriginalFilename()).orElseThrow(() -> invalid(BAD_FORMAT));
        if (upload.getSize() > MAX_BYTES) {
            throw invalid(TOO_BIG);
        }
        byte[] content = upload.getBytes();
        if (!kind.matches(content)) {
            throw invalid(BAD_CONTENT);
        }
        // El recorte central es el mismo en cualquier orientación: se gira al final, ya reducido.
        BufferedImage square = SquareResampler.fit(decode(content, kind), SIZE);
        return encode(orient(square, ExifOrientation.read(content, kind)));
    }

    private static PixelRows decode(byte[] content, ImageKind kind) {
        ImageReader reader = ImageIO.getImageReadersByFormatName(kind.format()).next();
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(content))) {
            reader.setInput(input);
            checkPixels(reader);
            return pixels(reader, content, kind);
        } catch (IOException exception) {
            throw invalid(NOT_PROCESSED);
        } finally {
            reader.dispose();
        }
    }

    /** Solo lee la cabecera: no reserva memoria para los píxeles. */
    private static void checkPixels(ImageReader reader) {
        long pixels;
        try {
            pixels = (long) reader.getWidth(0) * reader.getHeight(0);
        } catch (IOException | RuntimeException exception) {
            throw invalid(BAD_CONTENT);
        }
        if (pixels > MAX_PIXELS) {
            throw invalid(pixels > BOMB_PIXELS ? BAD_CONTENT : TOO_MANY_PIXELS);
        }
    }

    /** Pillow detecta un PNG o un WEBP dañado al validarlo; un JPEG, recién al procesarlo. */
    private static PixelRows pixels(ImageReader reader, byte[] content, ImageKind kind) {
        try {
            // Un JPEG cortado no lanza excepción: se lee con el resto en gris y solo lo avisa.
            boolean[] truncated = {false};
            reader.addIIOReadWarningListener((source, warning) ->
                    truncated[0] |= warning.contains("premature end of data"));
            JpegHeader jpeg = kind == ImageKind.JPEG ? JpegHeader.read(content) : null;
            PixelRows rows = jpeg != null && jpeg.components() == CMYK_COMPONENTS
                    ? PixelRows.ofCmyk(reader.readRaster(0, null), jpeg.ycck()) : PixelRows.of(reader.read(0));
            if (truncated[0]) {
                throw invalid(NOT_PROCESSED);
            }
            return rows;
        } catch (IOException | IllegalArgumentException | IllegalStateException | IndexOutOfBoundsException error) {
            throw invalid(kind == ImageKind.JPEG ? NOT_PROCESSED : BAD_CONTENT);
        }
    }

    private static BufferedImage orient(BufferedImage square, int orientation) {
        if (orientation == ExifOrientation.NORMAL) {
            return square;
        }
        BufferedImage result = new BufferedImage(SIZE, SIZE, square.getType());
        Graphics2D graphics = result.createGraphics();
        graphics.drawImage(square, transform(orientation), null);
        graphics.dispose();
        return result;
    }

    /** x' = m00·x + m01·y + m02, y' = m10·x + m11·y + m12. */
    private static AffineTransform transform(int orientation) {
        return switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, SIZE, 0);
            case 3 -> new AffineTransform(-1, 0, 0, -1, SIZE, SIZE);
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, SIZE);
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> new AffineTransform(0, 1, -1, 0, SIZE, 0);
            case 7 -> new AffineTransform(0, -1, -1, 0, SIZE, SIZE);
            case 8 -> new AffineTransform(0, -1, 1, 0, 0, SIZE);
            default -> new AffineTransform();
        };
    }

    private static byte[] encode(BufferedImage image) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName(ImageKind.WEBP.format()).next();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (var stream = new MemoryCacheImageOutputStream(output)) {
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parameters.setCompressionType("Lossy");
            parameters.setCompressionQuality(QUALITY);
            writer.setOutput(stream);
            writer.write(null, new IIOImage(image, null, null), parameters);
        } finally {
            writer.dispose();
        }
        return output.toByteArray();
    }

    private static InvalidFieldsException invalid(String message) {
        return InvalidFieldsException.field("avatar", message);
    }
}

package com.tecsup.palacetec.media;

import com.tecsup.palacetec.exception.InvalidFieldsException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
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
        BufferedImage square = crop(decode(content, kind));
        return encode(orient(square, ExifOrientation.read(content, kind)));
    }

    private static BufferedImage decode(byte[] content, ImageKind kind) {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName(kind.format());
        ImageReader reader = readers.next();
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(content))) {
            reader.setInput(input);
            checkPixels(reader);
            return pixels(reader, kind);
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

    /** Un PNG dañado ya lo detecta la validación de Pillow; en los otros formatos falla al procesarlo. */
    private static BufferedImage pixels(ImageReader reader, ImageKind kind) {
        try {
            // Un JPEG cortado no lanza excepción: se lee con el resto en gris y solo lo avisa.
            boolean[] truncated = {false};
            reader.addIIOReadWarningListener((source, warning) ->
                    truncated[0] |= warning.contains("premature end of data"));
            BufferedImage image = reader.read(0);
            if (truncated[0]) {
                throw invalid(NOT_PROCESSED);
            }
            return image;
        } catch (IOException | IllegalArgumentException | IllegalStateException | IndexOutOfBoundsException error) {
            throw invalid(kind == ImageKind.PNG ? BAD_CONTENT : NOT_PROCESSED);
        }
    }

    /** Cuadrado central reducido a 300 x 300. Reducir por mitades conserva el detalle que se perdería de un salto. */
    private static BufferedImage crop(BufferedImage image) {
        int side = Math.min(image.getWidth(), image.getHeight());
        int x = (image.getWidth() - side) / 2;
        int y = (image.getHeight() - side) / 2;
        // Como en Django: en una imagen con paleta se ignora la transparencia.
        boolean alpha = image.getColorModel().hasAlpha() && !(image.getColorModel() instanceof IndexColorModel);
        BufferedImage current = null;
        int size = side;
        do {
            int next = size >= 2 * SIZE ? size / 2 : SIZE;
            BufferedImage target = new BufferedImage(next, next,
                    alpha ? BufferedImage.TYPE_INT_ARGB_PRE : BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = target.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, size >= 2 * SIZE
                    ? RenderingHints.VALUE_INTERPOLATION_BILINEAR : RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            if (current == null) {
                graphics.drawImage(opaque(image), 0, 0, next, next, x, y, x + side, y + side, null);
            } else {
                graphics.drawImage(current, 0, 0, next, next, null);
            }
            graphics.dispose();
            current = target;
            size = next;
        } while (size != SIZE);
        return current;
    }

    /** Colores de la paleta sin su transparencia; las demás imágenes quedan igual. */
    private static BufferedImage opaque(BufferedImage image) {
        if (!(image.getColorModel() instanceof IndexColorModel palette) || !palette.hasAlpha()) {
            return image;
        }
        int[] colors = new int[palette.getMapSize()];
        palette.getRGBs(colors);
        for (int i = 0; i < colors.length; i++) {
            colors[i] |= 0xFF000000;
        }
        IndexColorModel solid = new IndexColorModel(palette.getPixelSize(), colors.length, colors, 0, false, -1,
                image.getRaster().getTransferType());
        return new BufferedImage(solid, image.getRaster(), false, null);
    }

    private static BufferedImage orient(BufferedImage square, int orientation) {
        BufferedImage result = new BufferedImage(SIZE, SIZE, square.getColorModel().hasAlpha()
                ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
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

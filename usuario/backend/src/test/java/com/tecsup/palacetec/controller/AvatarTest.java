package com.tecsup.palacetec.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tecsup.palacetec.ApiTest;
import com.tecsup.palacetec.Browser;
import com.tecsup.palacetec.media.MediaStorage;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;

/** HU65 de Django: foto JPG, PNG o WEBP de hasta 2 MB, recortada a 300 x 300 en WEBP. */
class AvatarTest extends ApiTest {

    private static final String AVATAR = "/api/v1/users/avatar";
    private static final String BAD_FORMAT = "La foto debe ser JPG, PNG o WEBP.";
    private static final String BAD_CONTENT = "El contenido del archivo no corresponde a una imagen válida.";

    @Autowired
    private MediaStorage media;

    @DynamicPropertySource
    static void mediaFolder(DynamicPropertyRegistry registry) throws IOException {
        Path folder = Files.createTempDirectory("palacetec-media");
        registry.add("palacetec.web.media-root", folder::toString);
    }

    /** Mitad izquierda roja y mitad derecha azul. */
    private static BufferedImage picture(int width, int height, int type) {
        BufferedImage image = new BufferedImage(width, height, type);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRect(0, 0, width, height);
        graphics.setColor(Color.BLUE);
        graphics.fillRect(width / 2, 0, width, height);
        graphics.dispose();
        return image;
    }

    private static byte[] encode(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, format, output)).isTrue();
        return output.toByteArray();
    }

    private static byte[] png() throws IOException {
        return encode(picture(600, 400, BufferedImage.TYPE_INT_RGB), "png");
    }

    private ResultActions upload(Player player, String name, String type, byte[] bytes) throws Exception {
        return mvc.perform(multipart(AVATAR).file(new MockMultipartFile("avatar", name, type, bytes))
                .with(player.bearer()));
    }

    private String uploaded(Player player, String name, String type, byte[] bytes) throws Exception {
        return json(upload(player, name, type, bytes).andExpect(status().isOk()).andReturn()).get("avatar_url").asText();
    }

    private Path file(String avatarUrl) {
        return media.root().resolve(avatarUrl.substring("/media/".length()));
    }

    private BufferedImage saved(String avatarUrl) throws IOException {
        byte[] bytes = Files.readAllBytes(file(avatarUrl));
        assertThat(new String(bytes, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("RIFF");
        assertThat(new String(bytes, 8, 4, StandardCharsets.US_ASCII)).isEqualTo("WEBP");
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        assertThat(image.getWidth()).isEqualTo(300);
        assertThat(image.getHeight()).isEqualTo(300);
        return image;
    }

    private static String color(BufferedImage image, int x, int y) {
        Color color = new Color(image.getRGB(x, y), true);
        if (color.getAlpha() < 60) {
            return "transparente";
        }
        return color.getRed() > 180 && color.getBlue() < 80 ? "rojo"
                : color.getBlue() > 180 && color.getRed() < 80 ? "azul" : color.toString();
    }

    @Test
    void aValidPngIsCroppedTo300AndStoredAsWebp() throws Exception {
        Player player = newPlayer();
        String url = json(upload(player, "foto.png", "image/png", png()).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8)).andExpect(jsonPath("$.email").value(player.email()))
                .andExpect(jsonPath("$.avatar_url", matchesPattern(
                        "/media/avatars/user_" + player.id() + "_[0-9a-f]{12}\\.webp"))).andReturn())
                .get("avatar_url").asText();
        BufferedImage image = saved(url);
        assertThat(color(image, 20, 150)).isEqualTo("rojo");
        assertThat(color(image, 280, 150)).isEqualTo("azul");
        mvc.perform(get("/api/v1/users/me").with(player.bearer())).andExpect(jsonPath("$.avatar_url").value(url));
    }

    @Test
    void thePhotoIsPublicAtItsUrl() throws Exception {
        String url = uploaded(newPlayer(), "foto.png", "image/png", png());
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().contentType("image/webp"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().bytes(Files.readAllBytes(file(url))));
        Browser browser = browser();
        browser.setCookie("access_token", "vencida");
        browser.read(url).andExpect(status().isOk());
        mvc.perform(get("/media/avatars/no-existe.webp")).andExpect(status().isNotFound());
        mvc.perform(get("/media/../application.yml")).andExpect(status().is4xxClientError());
    }

    @Test
    void acceptsJpgAndWebpOfAnySize() throws Exception {
        Player player = newPlayer();
        BufferedImage tall = picture(200, 500, BufferedImage.TYPE_INT_RGB);
        for (String[] format : new String[][] {{"foto.JPG", "IMAGE/JPEG", "jpeg"}, {"foto.jpeg", "image/jpeg", "jpeg"},
                {"foto.webp", "image/webp", "webp"}}) {
            BufferedImage image = saved(uploaded(player, format[0], format[1], encode(tall, format[2])));
            assertThat(color(image, 20, 150)).isEqualTo("rojo");
            assertThat(color(image, 280, 150)).isEqualTo("azul");
        }
        saved(uploaded(player, "grande.jpg", "image/jpeg", encode(picture(3000, 2000, BufferedImage.TYPE_INT_RGB),
                "jpeg")));
        saved(uploaded(player, "punto.png", "image/png", encode(picture(1, 1, BufferedImage.TYPE_INT_RGB), "png")));
        saved(uploaded(player, "gris.png", "image/png", encode(picture(40, 40, BufferedImage.TYPE_BYTE_GRAY), "png")));
    }

    @Test
    void keepsTheTransparencyExceptInPalettes() throws Exception {
        Player player = newPlayer();
        BufferedImage image = picture(400, 400, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 200; x++) {
            for (int y = 0; y < 400; y++) {
                image.setRGB(x, y, 0);
            }
        }
        BufferedImage stored = saved(uploaded(player, "foto.png", "image/png", encode(image, "png")));
        assertThat(color(stored, 20, 150)).isEqualTo("transparente");
        assertThat(color(stored, 280, 150)).isEqualTo("azul");

        byte[] red = {(byte) 255, 0};
        byte[] green = {0, 0};
        byte[] blue = {0, (byte) 255};
        byte[] alpha = {0, (byte) 255};
        BufferedImage palette = new BufferedImage(400, 400, BufferedImage.TYPE_BYTE_INDEXED,
                new java.awt.image.IndexColorModel(8, 2, red, green, blue, alpha));
        for (int x = 200; x < 400; x++) {
            for (int y = 0; y < 400; y++) {
                palette.getRaster().setSample(x, y, 0, 1);
            }
        }
        stored = saved(uploaded(player, "foto.png", "image/png", encode(palette, "png")));
        assertThat(color(stored, 20, 150)).isEqualTo("rojo");
        assertThat(color(stored, 280, 150)).isEqualTo("azul");
    }

    /** Java aclara los grises al convertirlos a RGB y no lee CMYK; Pillow no los altera. */
    @Test
    void keepsTheColorsOfGrayAndCmykImages() throws Exception {
        Player player = newPlayer();
        BufferedImage gray = new BufferedImage(80, 80, BufferedImage.TYPE_BYTE_GRAY);
        Arrays.fill(((java.awt.image.DataBufferByte) gray.getRaster().getDataBuffer()).getData(), (byte) 100);
        for (String format : new String[] {"png", "jpeg"}) {
            BufferedImage stored = saved(uploaded(player, "gris." + format, "image/" + format, encode(gray, format)));
            assertThat(new Color(stored.getRGB(150, 150)).getRed()).isBetween(97, 103);
        }
        var colors = new java.awt.image.ComponentColorModel(
                java.awt.color.ColorSpace.getInstance(java.awt.color.ColorSpace.CS_GRAY), true, false,
                java.awt.Transparency.TRANSLUCENT, java.awt.image.DataBuffer.TYPE_BYTE);
        BufferedImage grayAlpha = new BufferedImage(colors, colors.createCompatibleWritableRaster(80, 80), false, null);
        Arrays.fill(((java.awt.image.DataBufferByte) grayAlpha.getRaster().getDataBuffer()).getData(), (byte) 100);
        Color translucent = new Color(saved(uploaded(player, "gris.png", "image/png", encode(grayAlpha, "png")))
                .getRGB(150, 150), true);
        assertThat(translucent.getAlpha()).isBetween(97, 103);
        assertThat(translucent.getRed()).isBetween(95, 105);

        // JPEG en CMYK guardado por Pillow: mitad roja y mitad azul.
        byte[] cmyk = getClass().getResourceAsStream("/cmyk.jpg").readAllBytes();
        BufferedImage stored = saved(uploaded(player, "cmyk.jpg", "image/jpeg", cmyk));
        assertThat(color(stored, 20, 150)).isEqualTo("rojo");
        assertThat(color(stored, 280, 150)).isEqualTo("azul");
    }

    /** Dónde queda la mitad roja (la izquierda del archivo) según la orientación de la cámara. */
    @ParameterizedTest
    @CsvSource({"1, 20, 150", "2, 280, 150", "3, 280, 150", "4, 20, 150", "5, 150, 20", "6, 150, 20", "7, 150, 280",
            "8, 150, 280"})
    void appliesTheCameraOrientation(int orientation, int redX, int redY) throws Exception {
        Player player = newPlayer();
        byte[] jpeg = encode(picture(400, 300, BufferedImage.TYPE_INT_RGB), "jpeg");
        BufferedImage image = saved(uploaded(player, "foto.jpg", "image/jpeg", withExif(jpeg, orientation)));
        assertThat(color(image, redX, redY)).isEqualTo("rojo");
        assertThat(color(image, 299 - redX, 299 - redY)).isEqualTo("azul");
    }

    /** Inserta un bloque APP1 con la etiqueta de orientación justo después del inicio del JPEG. */
    private static byte[] withExif(byte[] jpeg, int orientation) {
        ByteBuffer exif = ByteBuffer.allocate(36);
        exif.putShort((short) 0xFFE1).putShort((short) 34).put("Exif\0\0".getBytes(StandardCharsets.US_ASCII));
        exif.put("MM".getBytes(StandardCharsets.US_ASCII)).putShort((short) 42).putInt(8);
        exif.putShort((short) 1).putShort((short) 0x0112).putShort((short) 3).putInt(1);
        exif.putShort((short) orientation).putShort((short) 0);
        return ByteBuffer.allocate(jpeg.length + exif.position()).put(jpeg, 0, 2).put(exif.array(), 0, exif.position())
                .put(jpeg, 2, jpeg.length - 2).array();
    }

    @ParameterizedTest
    @CsvSource({"foto.gif, image/gif", "foto.png, image/gif", "foto.gif, image/png", "foto, image/png",
            "foto.png, ", "foto.svg, image/svg+xml", "foto.png.exe, image/png"})
    void otherFormatsAreRejected(String name, String type) throws Exception {
        Player player = newPlayer();
        rejected(upload(player, name, type, png()), BAD_FORMAT);
        assertThat(jdbc.queryForObject("select avatar_url from users", String.class))
                .isEqualTo("/avatars/avatar-1.svg");
    }

    private void rejected(ResultActions answer, String message) throws Exception {
        answer.andExpect(status().isBadRequest()).andExpect(jsonPath("$.avatar", contains(message)))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void aFakeExtensionIsRejected() throws Exception {
        Player player = newPlayer();
        rejected(upload(player, "foto.png", "image/png", "<script>alert(1)</script>".repeat(10).getBytes()),
                BAD_CONTENT);
        byte[] jpeg = encode(picture(50, 50, BufferedImage.TYPE_INT_RGB), "jpeg");
        rejected(upload(player, "foto.png", "image/png", jpeg), BAD_CONTENT);
        rejected(upload(player, "foto.webp", "image/webp", "RIFFxxxxWAVE0000".getBytes()), BAD_CONTENT);
        rejected(upload(player, "foto.png", "image/png", new byte[0]), BAD_CONTENT);
    }

    @Test
    void damagedImagesAreRejected() throws Exception {
        Player player = newPlayer();
        byte[] png = png();
        byte[] onlySignature = Arrays.copyOf(png, 108);
        Arrays.fill(onlySignature, 8, 108, (byte) '0');
        rejected(upload(player, "foto.png", "image/png", onlySignature), BAD_CONTENT);
        rejected(upload(player, "foto.png", "image/png", Arrays.copyOf(png, png.length / 2)), BAD_CONTENT);
        byte[] webp = encode(picture(600, 400, BufferedImage.TYPE_INT_RGB), "webp");
        rejected(upload(player, "foto.webp", "image/webp", Arrays.copyOf(webp, webp.length / 2)), BAD_CONTENT);
        byte[] jpeg = encode(picture(600, 400, BufferedImage.TYPE_INT_RGB), "jpeg");
        rejected(upload(player, "foto.jpg", "image/jpeg", Arrays.copyOf(jpeg, jpeg.length / 2)),
                "No se pudo procesar la imagen.");
        assertThat(jdbc.queryForObject("select avatar_url from users", String.class))
                .isEqualTo("/avatars/avatar-1.svg");
    }

    @Test
    void filesOverTwoMegabytesAreRejected() throws Exception {
        byte[] heavy = Arrays.copyOf(png(), 2 * 1024 * 1024 + 1);
        rejected(upload(newPlayer(), "foto.png", "image/png", heavy), "La foto no debe superar 2 MB.");
    }

    @Test
    void tooManyPixelsAreRejectedWithoutDecodingThem() throws Exception {
        Player player = newPlayer();
        rejected(upload(player, "foto.png", "image/png", withSize(png(), 6000, 5000)),
                "La imagen tiene demasiados píxeles.");
        rejected(upload(player, "foto.png", "image/png", withSize(png(), 20000, 20000)), BAD_CONTENT);
    }

    /** Cambia el ancho y el alto declarados en la cabecera (y su CRC). */
    private static byte[] withSize(byte[] png, int width, int height) {
        byte[] changed = png.clone();
        ByteBuffer data = ByteBuffer.wrap(changed);
        data.putInt(16, width).putInt(20, height);
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        crc.update(changed, 12, 17);
        data.putInt(29, (int) crc.getValue());
        return changed;
    }

    @Test
    void aNewPhotoOrADefaultAvatarDeletesThePreviousFile() throws Exception {
        Player player = newPlayer();
        String first = uploaded(player, "foto.png", "image/png", png());
        String second = uploaded(player, "foto.png", "image/png", png());
        assertThat(second).isNotEqualTo(first);
        assertThat(file(first)).doesNotExist();
        assertThat(file(second)).exists();
        updateAvatar(player, "/media/avatars/otro.webp").andExpect(status().isBadRequest());
        updateAvatar(player, second).andExpect(status().isOk());
        assertThat(file(second)).exists();
        updateAvatar(player, "/avatars/avatar-3.svg").andExpect(status().isOk());
        assertThat(file(second)).doesNotExist();
    }

    private ResultActions updateAvatar(Player player, String avatar) throws Exception {
        return mvc.perform(put("/api/v1/users/profile").with(player.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"avatar_url\": \"" + avatar + "\"}"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/media/avatars/../../fuera.txt", "/media/otra/archivo.txt"})
    void neverDeletesOutsideTheAvatarFolder(String previous) throws Exception {
        Player player = newPlayer();
        Path outside = media.root().resolve(previous.substring("/media/".length())).normalize();
        Files.createDirectories(outside.getParent());
        Files.writeString(outside, "no borrar");
        jdbc.update("update users set avatar_url = ?", previous);
        uploaded(player, "foto.png", "image/png", png());
        assertThat(outside).exists();
        Files.delete(outside);
    }

    @Test
    void requiresTheFileAndTheSession() throws Exception {
        Player player = newPlayer();
        String message = "Selecciona una imagen JPG, PNG o WEBP.";
        rejected(mvc.perform(multipart(AVATAR).with(player.bearer())), message);
        rejected(mvc.perform(multipart(AVATAR).param("avatar", "texto").with(player.bearer())), message);
        rejected(upload(player, "", "application/octet-stream", new byte[0]), message);
        mvc.perform(post(AVATAR).with(player.bearer()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(content().json(
                        "{\"detail\": \"Unsupported media type \\\"application/json\\\" in request.\"}", true));
        mvc.perform(multipart(AVATAR).file(new MockMultipartFile("avatar", "foto.png", "image/png", png())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void worksWithTheCookieSessionAndCsrf() throws Exception {
        Player player = newPlayer();
        Browser browser = browser();
        browser.login(player.email(), PASSWORD);
        MockMultipartFile photo = new MockMultipartFile("avatar", "foto.png", "image/png", png());
        browser.write(multipart(AVATAR).file(photo)).andExpect(status().isOk());
        browser.withoutCsrf().write(multipart(AVATAR).file(photo)).andExpect(status().isForbidden());
    }
}

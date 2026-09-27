package grabber;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class NovelMetadataCoverTest {

    // The 1x1 WebP images browsers use for WebP feature detection.
    private static final String WEBP_LOSSLESS = "UklGRhoAAABXRUJQVlA4TA0AAAAvAAAAEAcQERGIiP4HAA==";
    private static final String WEBP_LOSSY = "UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA";

    private static BufferedImage decode(String base64) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(base64)));
    }

    @ParameterizedTest
    @ValueSource(strings = {WEBP_LOSSLESS, WEBP_LOSSY})
    void webpCoversCanBeDecoded(String webp) throws Exception {
        BufferedImage image = decode(webp);

        assertNotNull(image, "no ImageIO reader for WebP");
        assertEquals(1, image.getWidth());
        assertEquals(1, image.getHeight());
    }

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {
            "webp, jpg",   // readable through the plugin, but ImageIO cannot write it
            "avif, jpg",
            "null, png",
            "png,  png",
            "jpg,  jpg",
            "jpeg, jpeg",
            "gif,  gif",
    })
    void coverIsStoredInAFormatImageIoCanWrite(String extension, String expected) {
        assertEquals(expected, NovelMetadata.writableFormat(extension));
    }

    @ParameterizedTest
    @ValueSource(strings = {WEBP_LOSSLESS, WEBP_LOSSY})
    void decodedWebpCoverIsWrittenAsJpegForTheEpub(String webp) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BufferedImage cover = NovelMetadata.withoutAlpha(decode(webp));

        // EPUB.java writes the cover with ImageIO.write(cover, coverFormat, ...)
        assertTrue(ImageIO.write(cover, NovelMetadata.writableFormat("webp"), out));
        byte[] jpeg = out.toByteArray();
        assertTrue(jpeg.length > 2 && (jpeg[0] & 0xff) == 0xFF && (jpeg[1] & 0xff) == 0xD8, "not a JPEG");
    }

    @Test
    void transparentCoverIsFlattenedOntoWhiteForJpeg() throws Exception {
        BufferedImage transparent = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        // The JPEG writer refuses images with an alpha channel and returns false.
        assertFalse(ImageIO.write(transparent, "jpg", new ByteArrayOutputStream()));

        BufferedImage flattened = NovelMetadata.withoutAlpha(transparent);

        assertFalse(flattened.getColorModel().hasAlpha());
        assertEquals(0xFFFFFF, flattened.getRGB(0, 0) & 0xFFFFFF);
        assertTrue(ImageIO.write(flattened, "jpg", new ByteArrayOutputStream()));
    }

    @Test
    void opaqueCoverIsKeptAsIs() {
        BufferedImage opaque = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);

        assertSame(opaque, NovelMetadata.withoutAlpha(opaque));
    }
}

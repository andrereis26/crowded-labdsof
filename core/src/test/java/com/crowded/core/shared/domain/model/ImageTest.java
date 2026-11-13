package com.crowded.core.shared.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crowded.core.shared.domain.exception.RuleViolationException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ImageTest {

  /** Text that stands for the location and the camera a file's metadata reveals. */
  private static final String SECRET = "GPS 41.1579N 8.6291W Pixel 9";

  @Test
  void removesTheExifAndCommentsOfAJpegButKeepsItsOrientation() throws IOException {
    byte[] file = withSegments(picture("jpg"), exif(6), comment());

    Image image = Image.uploaded(file);

    assertThat(image.type()).isEqualTo(ImageType.JPEG);
    assertThat(image.mediaType()).isEqualTo("image/jpeg");
    assertThat(text(image.content())).doesNotContain(SECRET);
    assertThat(orientationOf(image.content())).isEqualTo(6);
    assertThat(ImageIO.read(new ByteArrayInputStream(image.content()))).isNotNull();
  }

  @Test
  void addsNoOrientationToAJpegThatHadNone() throws IOException {
    Image image = Image.uploaded(withSegments(picture("jpg"), exif(1)));

    assertThat(text(image.content())).doesNotContain("Exif").doesNotContain(SECRET);
    assertThat(ImageIO.read(new ByteArrayInputStream(image.content()))).isNotNull();
  }

  @Test
  void removesTheTextAndExifChunksOfAPng() throws IOException {
    byte[] png = picture("png");
    byte[] file =
        beforeEnd(
            png,
            chunk("tEXt", ("Location\0" + SECRET).getBytes(StandardCharsets.ISO_8859_1)),
            chunk("eXIf", SECRET.getBytes(StandardCharsets.ISO_8859_1)));

    Image image = Image.uploaded(file);

    assertThat(image.type()).isEqualTo(ImageType.PNG);
    assertThat(text(image.content())).doesNotContain(SECRET);
    assertThat(image.content()).isEqualTo(png);
    assertThat(ImageIO.read(new ByteArrayInputStream(image.content()))).isNotNull();
  }

  @Test
  void removesTheExifAndXmpChunksOfAWebpAndTheirFlags() {
    byte[] vp8x = {0x0C, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    byte[] file =
        webp(
            webpChunk("VP8X", vp8x),
            webpChunk("VP8L", new byte[] {0x2F, 0, 0, 0, 0}),
            webpChunk("EXIF", SECRET.getBytes(StandardCharsets.ISO_8859_1)),
            webpChunk("XMP ", SECRET.getBytes(StandardCharsets.ISO_8859_1)));

    Image image = Image.uploaded(file);

    byte[] content = image.content();
    assertThat(image.type()).isEqualTo(ImageType.WEBP);
    assertThat(text(content)).doesNotContain(SECRET).doesNotContain("EXIF").contains("VP8L");
    assertThat(content[20]).isZero();
    assertThat(littleEndian(content, 4)).isEqualTo(content.length - 8);
  }

  @Test
  void refusesAFileThatIsNotAnAcceptedImage() {
    assertRefused("GIF89a......".getBytes(StandardCharsets.ISO_8859_1), "IMAGE_TYPE_NOT_ACCEPTED");
    assertRefused("%PDF-1.7".getBytes(StandardCharsets.ISO_8859_1), "IMAGE_TYPE_NOT_ACCEPTED");
    assertRefused(new byte[0], "IMAGE_REQUIRED");
    assertRefused(null, "IMAGE_REQUIRED");
  }

  @Test
  void refusesAnImageOfMoreThanFiveMegabytes() throws IOException {
    byte[] large = Arrays.copyOf(picture("png"), Image.MAX_BYTES + 1);

    assertRefused(large, "IMAGE_TOO_LARGE");
  }

  @Test
  void refusesAFileWhoseStructureIsBroken() throws IOException {
    byte[] jpeg = picture("jpg");
    byte[] png = picture("png");
    byte[] badChecksum = png.clone();
    badChecksum[30] ^= 0x01;

    assertRefused(Arrays.copyOf(jpeg, 20), "IMAGE_INVALID");
    assertRefused(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9}, "IMAGE_INVALID");
    assertRefused(Arrays.copyOf(png, 40), "IMAGE_INVALID");
    assertRefused(badChecksum, "IMAGE_INVALID");
    assertRefused(webp(webpChunk("EXIF", new byte[] {1, 2})), "IMAGE_INVALID");
    assertRefused(
        "RIFFÿÿ\u0000\u0000WEBPVP8L".getBytes(StandardCharsets.ISO_8859_1), "IMAGE_INVALID");
  }

  @Test
  void isEqualByTypeAndContentAndRestoresAsStored() throws IOException {
    byte[] png = picture("png");
    Image image = Image.uploaded(png);
    byte[] copy = image.content();
    copy[0] = 0;

    assertThat(Image.restore("image/png", png)).isEqualTo(image).hasSameHashCodeAs(image);
    assertThat(image.content()).isEqualTo(png);
    assertThat(image.size()).isEqualTo(png.length);
    assertThat(image).hasToString("Image[PNG, " + png.length + " bytes]");
    assertThat(Image.restore("image/jpeg", png)).isNotEqualTo(image);
    assertThatThrownBy(() -> Image.restore("image/gif", png))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new Image(ImageType.PNG, new byte[0]))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static void assertRefused(byte[] file, String code) {
    assertThatThrownBy(() -> Image.uploaded(file))
        .isInstanceOfSatisfying(
            RuleViolationException.class, e -> assertThat(e.getCode()).isEqualTo(code));
  }

  /** A small real picture, encoded by the JDK. */
  private static byte[] picture(String format) throws IOException {
    BufferedImage image = new BufferedImage(16, 8, BufferedImage.TYPE_INT_RGB);
    image.setRGB(3, 3, 0x336699);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, format, out);
    return out.toByteArray();
  }

  /** The JPEG with these segments right after its start. */
  private static byte[] withSegments(byte[] jpeg, byte[]... segments) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(jpeg, 0, 2);
    for (byte[] segment : segments) {
      out.writeBytes(segment);
    }
    out.write(jpeg, 2, jpeg.length - 2);
    return out.toByteArray();
  }

  /** An APP1 EXIF segment, little-endian, with an orientation and a tag holding the secret. */
  private static byte[] exif(int orientation) {
    byte[] secret = SECRET.getBytes(StandardCharsets.ISO_8859_1);
    ByteArrayOutputStream tiff = new ByteArrayOutputStream();
    tiff.writeBytes(new byte[] {'I', 'I', 0x2A, 0, 8, 0, 0, 0, 2, 0});
    tiff.writeBytes(new byte[] {0x12, 0x01, 3, 0, 1, 0, 0, 0, (byte) orientation, 0, 0, 0});
    int offset = 8 + 2 + 24 + 4;
    tiff.writeBytes(
        new byte[] {0x0E, 0x01, 2, 0, (byte) secret.length, 0, 0, 0, (byte) offset, 0, 0, 0});
    tiff.writeBytes(new byte[] {0, 0, 0, 0});
    tiff.writeBytes(secret);
    byte[] data = concat("Exif\0\0".getBytes(StandardCharsets.ISO_8859_1), tiff.toByteArray());
    return segment(0xE1, data);
  }

  private static byte[] comment() {
    return segment(0xFE, SECRET.getBytes(StandardCharsets.ISO_8859_1));
  }

  private static byte[] segment(int marker, byte[] data) {
    int length = data.length + 2;
    return concat(
        new byte[] {(byte) 0xFF, (byte) marker, (byte) (length >> 8), (byte) length}, data);
  }

  /** The orientation of the first APP1 segment, read where the stripped file writes it. */
  private static int orientationOf(byte[] jpeg) {
    for (int i = 2; i + 1 < jpeg.length; i++) {
      if ((jpeg[i] & 0xFF) == 0xFF && (jpeg[i + 1] & 0xFF) == 0xE1) {
        return jpeg[i + 4 + 6 + 8 + 2 + 8 + 1] & 0xFF;
      }
    }
    return 1;
  }

  private static byte[] chunk(String type, byte[] data) {
    byte[] typeBytes = type.getBytes(StandardCharsets.ISO_8859_1);
    CRC32 crc = new CRC32();
    crc.update(typeBytes);
    crc.update(data);
    long value = crc.getValue();
    return concat(bigEndian(data.length), typeBytes, data, bigEndian((int) value));
  }

  /** The PNG with these chunks before its end. */
  private static byte[] beforeEnd(byte[] png, byte[]... chunks) {
    int end = png.length - 12;
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(png, 0, end);
    for (byte[] chunk : chunks) {
      out.writeBytes(chunk);
    }
    out.write(png, end, 12);
    return out.toByteArray();
  }

  private static byte[] webpChunk(String fourcc, byte[] data) {
    byte[] padded = data.length % 2 == 0 ? data : Arrays.copyOf(data, data.length + 1);
    byte[] size = {(byte) data.length, (byte) (data.length >> 8), (byte) (data.length >> 16), 0};
    return concat(fourcc.getBytes(StandardCharsets.ISO_8859_1), size, padded);
  }

  private static byte[] webp(byte[]... chunks) {
    byte[] body = concat(chunks);
    int size = 4 + body.length;
    return concat(
        "RIFF".getBytes(StandardCharsets.ISO_8859_1),
        new byte[] {(byte) size, (byte) (size >> 8), (byte) (size >> 16), (byte) (size >> 24)},
        "WEBP".getBytes(StandardCharsets.ISO_8859_1),
        body);
  }

  private static int littleEndian(byte[] in, int i) {
    return (in[i] & 0xFF) | (in[i + 1] & 0xFF) << 8 | (in[i + 2] & 0xFF) << 16 | in[i + 3] << 24;
  }

  private static byte[] bigEndian(int value) {
    return new byte[] {
      (byte) (value >> 24), (byte) (value >> 16), (byte) (value >> 8), (byte) value
    };
  }

  private static byte[] concat(byte[]... parts) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (byte[] part : parts) {
      out.writeBytes(part);
    }
    return out.toByteArray();
  }

  private static String text(byte[] content) {
    return new String(content, StandardCharsets.ISO_8859_1);
  }
}

package com.crowded.core.shared.domain.model;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.zip.CRC32;

/**
 * Removes the metadata of an image from its file structure, without decoding the picture (ADR
 * 0019), and refuses a file whose structure is broken.
 */
final class ImageMetadata {

  /** PNG chunks that draw the picture; text, EXIF and time chunks are left out. */
  private static final Set<String> PNG_KEPT =
      Set.of(
          "IHDR", "PLTE", "IDAT", "IEND", "tRNS", "gAMA", "cHRM", "sRGB", "iCCP", "sBIT", "pHYs",
          "bKGD", "acTL", "fcTL", "fdAT");

  private static final int JPEG_APP0 = 0xE0;
  private static final int JPEG_APP1 = 0xE1;
  private static final int JPEG_APP2 = 0xE2;
  private static final int JPEG_APP14 = 0xEE;
  private static final int JPEG_APP15 = 0xEF;
  private static final int JPEG_COMMENT = 0xFE;
  private static final int JPEG_START_OF_SCAN = 0xDA;
  private static final int JPEG_END = 0xD9;
  private static final int EXIF_ORIENTATION = 0x0112;

  private ImageMetadata() {}

  /**
   * The file without its metadata.
   *
   * @throws IllegalArgumentException when its structure is broken
   */
  static byte[] strip(ImageType type, byte[] file) {
    try {
      return switch (type) {
        case JPEG -> jpeg(file);
        case PNG -> png(file);
        case WEBP -> webp(file);
      };
    } catch (IndexOutOfBoundsException e) {
      throw broken();
    }
  }

  /**
   * Keeps the segments that draw the picture (JFIF, the colour profile, Adobe's colour transform
   * and everything from the start of the scan) and the orientation, so a phone's photo is not shown
   * sideways. Drops EXIF, XMP, IPTC and comments.
   */
  private static byte[] jpeg(byte[] in) {
    ByteArrayOutputStream head = new ByteArrayOutputStream();
    ByteArrayOutputStream rest = new ByteArrayOutputStream(in.length);
    int orientation = 1;
    int i = 2;
    while (true) {
      if (u8(in, i) != 0xFF) {
        throw broken();
      }
      int marker = u8(in, i + 1);
      while (marker == 0xFF) {
        i++;
        marker = u8(in, i + 1);
      }
      if (marker == JPEG_END) {
        throw broken();
      }
      if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
        rest.write(in, i, 2);
        i += 2;
        continue;
      }
      int length = u16(in, i + 2);
      int end = i + 2 + length;
      if (length < 2 || end > in.length) {
        throw broken();
      }
      if (marker == JPEG_START_OF_SCAN) {
        rest.write(in, i, in.length - i);
        break;
      }
      if (marker == JPEG_APP1 && orientation == 1) {
        orientation = exifOrientation(in, i + 4, length - 2);
      }
      boolean application = marker >= JPEG_APP0 && marker <= JPEG_APP15;
      boolean kept =
          application
              ? marker == JPEG_APP0 || marker == JPEG_APP2 || marker == JPEG_APP14
              : marker != JPEG_COMMENT;
      if (kept) {
        // JFIF stays the first segment, before the orientation.
        (marker == JPEG_APP0 && head.size() == 0 && rest.size() == 0 ? head : rest)
            .write(in, i, end - i);
      }
      i = end;
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream(in.length);
    out.write(0xFF);
    out.write(0xD8);
    out.writeBytes(head.toByteArray());
    if (orientation != 1) {
      out.writeBytes(orientationSegment(orientation));
    }
    out.writeBytes(rest.toByteArray());
    return out.toByteArray();
  }

  /** The orientation tag of an APP1 EXIF segment's first directory; 1 when there is none. */
  private static int exifOrientation(byte[] in, int start, int length) {
    int end = start + length;
    if (length < 14 || !"Exif\0\0".equals(new String(in, start, 6, StandardCharsets.ISO_8859_1))) {
      return 1;
    }
    int tiff = start + 6;
    boolean little = in[tiff] == 'I' && in[tiff + 1] == 'I';
    boolean big = in[tiff] == 'M' && in[tiff + 1] == 'M';
    if (!little && !big) {
      return 1;
    }
    long directory = tiff + u32(in, tiff + 4, little);
    if (directory + 2 > end) {
      return 1;
    }
    int entries = u16(in, (int) directory, little);
    for (int e = 0; e < entries; e++) {
      int entry = (int) directory + 2 + e * 12;
      if (entry + 12 > end) {
        return 1;
      }
      if (u16(in, entry, little) == EXIF_ORIENTATION && u16(in, entry + 2, little) == 3) {
        int value = u16(in, entry + 8, little);
        return value >= 1 && value <= 8 ? value : 1;
      }
    }
    return 1;
  }

  /** An APP1 EXIF segment with nothing but the orientation, in big-endian order. */
  private static byte[] orientationSegment(int orientation) {
    byte[] exif = {
      'E',
      'x',
      'i',
      'f',
      0,
      0,
      'M',
      'M',
      0,
      0x2A,
      0,
      0,
      0,
      8,
      0,
      1,
      0x01,
      0x12,
      0,
      3,
      0,
      0,
      0,
      1,
      0,
      (byte) orientation,
      0,
      0,
      0,
      0,
      0,
      0
    };
    ByteArrayOutputStream segment = new ByteArrayOutputStream();
    segment.write(0xFF);
    segment.write(JPEG_APP1);
    segment.write((exif.length + 2) >> 8);
    segment.write((exif.length + 2) & 0xFF);
    segment.writeBytes(exif);
    return segment.toByteArray();
  }

  /**
   * Keeps the chunks that draw the picture, after checking each one's checksum, and drops text,
   * EXIF and time chunks and anything after the end.
   */
  private static byte[] png(byte[] in) {
    ByteArrayOutputStream out = new ByteArrayOutputStream(in.length);
    out.write(in, 0, 8);
    int i = 8;
    boolean data = false;
    boolean first = true;
    while (true) {
      long length = u32(in, i, false);
      if (length > Integer.MAX_VALUE || i + 12 + length > in.length) {
        throw broken();
      }
      String type = new String(in, i + 4, 4, StandardCharsets.ISO_8859_1);
      CRC32 crc = new CRC32();
      crc.update(in, i + 4, 4 + (int) length);
      if (crc.getValue() != u32(in, i + 8 + (int) length, false)) {
        throw broken();
      }
      if (first != "IHDR".equals(type)) {
        throw broken();
      }
      first = false;
      data |= "IDAT".equals(type);
      int end = i + 12 + (int) length;
      if (PNG_KEPT.contains(type)) {
        out.write(in, i, end - i);
      }
      if ("IEND".equals(type)) {
        break;
      }
      i = end;
    }
    if (!data) {
      throw broken();
    }
    return out.toByteArray();
  }

  /**
   * Drops the EXIF and XMP chunks and their flags in the extended header, and rewrites the RIFF
   * size.
   */
  private static byte[] webp(byte[] in) {
    long riff = u32(in, 4, true);
    if (riff + 8 > in.length || riff < 4) {
      throw broken();
    }
    int limit = (int) riff + 8;
    ByteArrayOutputStream chunks = new ByteArrayOutputStream(in.length);
    boolean picture = false;
    int i = 12;
    while (i < limit) {
      String fourcc = new String(in, i, 4, StandardCharsets.ISO_8859_1);
      long size = u32(in, i + 4, true);
      long padded = size + (size & 1);
      if (i + 8 + padded > limit) {
        throw broken();
      }
      int end = i + 8 + (int) padded;
      switch (fourcc) {
        case "EXIF", "XMP " -> {
          // Left out.
        }
        case "VP8X" -> {
          byte[] chunk = java.util.Arrays.copyOfRange(in, i, end);
          chunk[8] &= (byte) ~0x0C;
          chunks.writeBytes(chunk);
        }
        default -> {
          picture |= "VP8 ".equals(fourcc) || "VP8L".equals(fourcc) || "ANMF".equals(fourcc);
          chunks.write(in, i, end - i);
        }
      }
      i = end;
    }
    if (!picture) {
      throw broken();
    }
    int size = 4 + chunks.size();
    ByteArrayOutputStream out = new ByteArrayOutputStream(8 + size);
    out.writeBytes("RIFF".getBytes(StandardCharsets.ISO_8859_1));
    out.write(size & 0xFF);
    out.write((size >> 8) & 0xFF);
    out.write((size >> 16) & 0xFF);
    out.write((size >> 24) & 0xFF);
    out.writeBytes("WEBP".getBytes(StandardCharsets.ISO_8859_1));
    out.writeBytes(chunks.toByteArray());
    return out.toByteArray();
  }

  private static IllegalArgumentException broken() {
    return new IllegalArgumentException("The image's structure is broken");
  }

  private static int u8(byte[] in, int i) {
    return in[i] & 0xFF;
  }

  private static int u16(byte[] in, int i) {
    return u16(in, i, false);
  }

  private static int u16(byte[] in, int i, boolean little) {
    return little ? u8(in, i) | u8(in, i + 1) << 8 : u8(in, i) << 8 | u8(in, i + 1);
  }

  private static long u32(byte[] in, int i, boolean little) {
    long value = 0;
    for (int b = 0; b < 4; b++) {
      value = little ? value | (long) u8(in, i + b) << (8 * b) : value << 8 | u8(in, i + b);
    }
    return value;
  }
}

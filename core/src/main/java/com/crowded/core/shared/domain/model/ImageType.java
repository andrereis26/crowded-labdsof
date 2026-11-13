package com.crowded.core.shared.domain.model;

import java.util.Arrays;
import java.util.Optional;

/** The image formats Crowded accepts for uploads, recognised from a file's first bytes. */
public enum ImageType {
  JPEG("image/jpeg"),
  PNG("image/png"),
  WEBP("image/webp");

  private static final byte[] PNG_SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

  private final String mediaType;

  ImageType(String mediaType) {
    this.mediaType = mediaType;
  }

  /** The media type sent with the image, such as {@code image/jpeg}. */
  public String mediaType() {
    return mediaType;
  }

  /** The type with this media type, as stored. */
  public static ImageType ofMediaType(String mediaType) {
    return Arrays.stream(values())
        .filter(t -> t.mediaType.equals(mediaType))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Not an image type: " + mediaType));
  }

  /** The type the file's content starts with, whatever its name or declared type say. */
  static Optional<ImageType> of(byte[] file) {
    if (file.length >= 3
        && (file[0] & 0xFF) == 0xFF
        && (file[1] & 0xFF) == 0xD8
        && (file[2] & 0xFF) == 0xFF) {
      return Optional.of(JPEG);
    }
    if (file.length >= PNG_SIGNATURE.length
        && Arrays.equals(file, 0, PNG_SIGNATURE.length, PNG_SIGNATURE, 0, PNG_SIGNATURE.length)) {
      return Optional.of(PNG);
    }
    if (file.length >= 12 && ascii(file, 0, "RIFF") && ascii(file, 8, "WEBP")) {
      return Optional.of(WEBP);
    }
    return Optional.empty();
  }

  private static boolean ascii(byte[] file, int offset, String text) {
    for (int i = 0; i < text.length(); i++) {
      if (file[offset + i] != text.charAt(i)) {
        return false;
      }
    }
    return true;
  }
}

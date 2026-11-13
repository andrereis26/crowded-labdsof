package com.crowded.core.shared.domain.model;

import com.crowded.core.shared.domain.exception.RuleViolationException;
import java.util.Arrays;
import java.util.Objects;

/**
 * An uploaded image as Crowded keeps it: a JPEG, PNG or WebP of up to 5 MB, without its metadata
 * (ADR 0019). Equal by type and content.
 */
public record Image(ImageType type, byte[] content) {

  /** The largest image accepted: 5 MB. */
  public static final int MAX_BYTES = 5 * 1024 * 1024;

  public Image {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(content, "content");
    if (content.length == 0 || content.length > MAX_BYTES) {
      throw new IllegalArgumentException("An image has 1 byte to 5 MB");
    }
    content = content.clone();
  }

  /**
   * The image a user uploaded: its type comes from its content, and its metadata (EXIF, XMP, text,
   * and so the location) is removed.
   *
   * @throws RuleViolationException when the file is empty, larger than 5 MB, not a JPEG, PNG or
   *     WebP, or not a well-formed one
   */
  public static Image uploaded(byte[] file) {
    if (file == null || file.length == 0) {
      throw new RuleViolationException("IMAGE_REQUIRED", "Choose an image to upload.");
    }
    if (file.length > MAX_BYTES) {
      throw new RuleViolationException("IMAGE_TOO_LARGE", "The image can have at most 5 MB.");
    }
    ImageType type =
        ImageType.of(file)
            .orElseThrow(
                () ->
                    new RuleViolationException(
                        "IMAGE_TYPE_NOT_ACCEPTED", "The image must be a JPEG, PNG or WebP file."));
    try {
      return new Image(type, ImageMetadata.strip(type, file));
    } catch (IllegalArgumentException e) {
      throw new RuleViolationException("IMAGE_INVALID", "The file is not a valid image.");
    }
  }

  /** An image as it was stored. */
  public static Image restore(String mediaType, byte[] content) {
    return new Image(ImageType.ofMediaType(mediaType), content);
  }

  /** A copy of the image's bytes. */
  @Override
  public byte[] content() {
    return content.clone();
  }

  public String mediaType() {
    return type.mediaType();
  }

  public int size() {
    return content.length;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof Image image
        && type == image.type
        && Arrays.equals(content, image.content);
  }

  @Override
  public int hashCode() {
    return 31 * type.hashCode() + Arrays.hashCode(content);
  }

  @Override
  public String toString() {
    return "Image[" + type + ", " + content.length + " bytes]";
  }
}

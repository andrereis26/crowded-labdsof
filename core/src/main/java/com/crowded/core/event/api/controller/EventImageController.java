package com.crowded.core.event.api.controller;

import com.crowded.core.event.application.service.EventImageService;
import com.crowded.core.event.domain.model.EventImage;
import com.crowded.core.shared.api.controller.CurrentUser;
import com.crowded.core.shared.domain.model.Image;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

/**
 * An event's image (API Outline, journey 1). The request body is the file itself; Core reads its
 * type from its content, so the declared {@code Content-Type} does not matter.
 */
@RestController
public class EventImageController {

  private final EventImageService service;

  EventImageController(EventImageService service) {
    this.service = service;
  }

  /** Uploads or replaces the event's image: 204. */
  @PutMapping("/events/{eventId}/image")
  public ResponseEntity<Void> replace(
      @PathVariable UUID eventId, @AuthenticationPrincipal Jwt token, HttpServletRequest request)
      throws IOException {
    service.replace(CurrentUser.id(token), eventId, body(request));
    return ResponseEntity.noContent().build();
  }

  /** Removes the event's image: 204, also when it had none. */
  @DeleteMapping("/events/{eventId}/image")
  public ResponseEntity<Void> remove(
      @PathVariable UUID eventId, @AuthenticationPrincipal Jwt token) {
    service.remove(CurrentUser.id(token), eventId);
    return ResponseEntity.noContent().build();
  }

  /**
   * The event's image, kept by the browser privately and checked again each time: 304 while it is
   * the same.
   */
  @GetMapping("/events/{eventId}/image")
  public ResponseEntity<byte[]> image(
      @PathVariable UUID eventId, @AuthenticationPrincipal Jwt token, WebRequest request) {
    EventImage image = service.find(CurrentUser.id(token), eventId);
    String etag = "\"" + image.getUpdatedAt().toEpochMilli() + "\"";
    if (request.checkNotModified(etag)) {
      return ResponseEntity.status(304).eTag(etag).build();
    }
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(image.getImage().mediaType()))
        .cacheControl(CacheControl.noCache().cachePrivate())
        .eTag(etag)
        .body(image.getImage().content());
  }

  /**
   * At most one byte more than an image may have, so a larger upload is refused without keeping it.
   * The rest is read and dropped, so the client gets the answer; the router caps the body at 10 MB.
   */
  private static byte[] body(HttpServletRequest request) throws IOException {
    try (InputStream in = request.getInputStream()) {
      byte[] file = in.readNBytes(Image.MAX_BYTES + 1);
      in.transferTo(OutputStream.nullOutputStream());
      return file;
    }
  }
}

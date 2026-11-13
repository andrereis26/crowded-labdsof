package com.crowded.core.ticket.infrastructure.config;

import com.crowded.core.ticket.domain.model.ReadTicket;
import com.crowded.core.ticket.domain.repository.TicketFileReader;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.ReaderException;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * Reads a ticket PDF with PDFBox, and its QR code with ZXing, without AI: the text, and the first
 * QR code found on its pages. The file stays in memory and is never written to disk.
 */
@Component
class PdfTicketFileReader implements TicketFileReader {

  /** Pages rendered to look for the QR code, so a long file cannot keep Core busy. */
  static final int MAX_PAGES = 10;

  // Enough resolution for the QR code of a printed ticket.
  private static final float RENDER_DPI = 200;

  /**
   * The most pixels a rendered page may have: an A4 page at 200 dpi has about 3.9 million, and a
   * small file can declare a huge page that would not fit in memory.
   */
  static final double MAX_PIXELS_PER_PAGE = 16_000_000;

  private static final float POINTS_PER_INCH = 72;

  private static final Map<DecodeHintType, Object> HINTS =
      Map.of(DecodeHintType.TRY_HARDER, Boolean.TRUE);

  @Override
  public Optional<ReadTicket> read(byte[] file) {
    try (PDDocument document = Loader.loadPDF(file)) {
      return findQrCode(document).map(entryCode -> new ReadTicket(entryCode, textOf(document)));
    } catch (IOException e) {
      // Not a PDF, damaged or password-protected.
      return Optional.empty();
    }
  }

  private static Optional<String> findQrCode(PDDocument document) throws IOException {
    PDFRenderer renderer = new PDFRenderer(document);
    int pages = Math.min(document.getNumberOfPages(), MAX_PAGES);
    for (int page = 0; page < pages; page++) {
      float dpi = renderDpi(document.getPage(page).getMediaBox());
      Optional<String> code = decode(renderer.renderImageWithDPI(page, dpi, ImageType.GRAY));
      if (code.isPresent()) {
        return code;
      }
    }
    return Optional.empty();
  }

  /** {@link #RENDER_DPI}, lowered for a page so large it would exceed the pixel limit. */
  static float renderDpi(PDRectangle page) {
    double inches = (page.getWidth() / POINTS_PER_INCH) * (page.getHeight() / POINTS_PER_INCH);
    if (inches <= 0) {
      return RENDER_DPI;
    }
    return (float) Math.min(RENDER_DPI, Math.sqrt(MAX_PIXELS_PER_PAGE / inches));
  }

  private static Optional<String> decode(BufferedImage image) {
    int width = image.getWidth();
    int height = image.getHeight();
    LuminanceSource source =
        new RGBLuminanceSource(width, height, image.getRGB(0, 0, width, height, null, 0, width));
    try {
      String text =
          new QRCodeReader().decode(new BinaryBitmap(new HybridBinarizer(source)), HINTS).getText();
      return text == null || text.isBlank() ? Optional.empty() : Optional.of(text);
    } catch (ReaderException e) {
      return Optional.empty();
    }
  }

  private static String textOf(PDDocument document) {
    try {
      return new PDFTextStripper().getText(document);
    } catch (IOException e) {
      // The QR code is what the import needs; without text, the AI just finds nothing.
      return "";
    }
  }
}

package com.crowded.core.ticket;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;

/**
 * Synthetic ticket PDFs for the tests, made on the fly with invented data, so no real ticket or
 * personal data is committed (Responsible AI Assessment, evaluation approach).
 */
public final class TicketPdfs {

  /** The text of a ticket, with invented personal data that must never reach the AI. */
  public static final List<String> TICKET_TEXT =
      List.of(
          "Festival Margens do Douro",
          "Código do evento: MARG-2026",
          "Nome: Joana Silva",
          "Email: joana.silva@exemplo.pt",
          "NIF: 123456789",
          "Porta A  Setor 2  Lugar 14");

  private TicketPdfs() {}

  /** One page with the text and a QR code holding {@code entryCode}. */
  public static byte[] ticket(String entryCode) {
    return pdf(List.of(new Page(TICKET_TEXT, entryCode, PDRectangle.A4)));
  }

  /** Like {@link #ticket}, with {@code eventCode} printed as the event code. */
  public static byte[] ticketOfEvent(String eventCode, String entryCode) {
    List<String> lines =
        TICKET_TEXT.stream()
            .map(
                line ->
                    line.startsWith("Código do evento:") ? "Código do evento: " + eventCode : line)
            .toList();
    return pdf(List.of(new Page(lines, entryCode, PDRectangle.A4)));
  }

  /** One page of {@code size} with the text and a QR code holding {@code entryCode}. */
  public static byte[] ticketOnPageOfSize(PDRectangle size, String entryCode) {
    return pdf(List.of(new Page(TICKET_TEXT, entryCode, size)));
  }

  /** The text on page 1 and the QR code on page {@code qrPage} (1-based). */
  public static byte[] ticketWithQrOnPage(int qrPage, String entryCode) {
    List<Page> pages = new ArrayList<>();
    for (int page = 1; page <= qrPage; page++) {
      pages.add(
          new Page(
              page == 1 ? TICKET_TEXT : List.of(),
              page == qrPage ? entryCode : null,
              PDRectangle.A4));
    }
    return pdf(pages);
  }

  /** One page with the text and no QR code. */
  public static byte[] withoutQrCode() {
    return pdf(List.of(new Page(TICKET_TEXT, null, PDRectangle.A4)));
  }

  private record Page(List<String> lines, String qrCode, PDRectangle size) {}

  private static byte[] pdf(List<Page> pages) {
    try (PDDocument document = new PDDocument();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
      for (Page content : pages) {
        PDPage page = new PDPage(content.size());
        document.addPage(page);
        try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
          stream.beginText();
          stream.setFont(font, 12);
          stream.setLeading(16);
          stream.newLineAtOffset(50, 780);
          for (String line : content.lines()) {
            stream.showText(line);
            stream.newLine();
          }
          stream.endText();
          if (content.qrCode() != null) {
            stream.drawImage(
                LosslessFactory.createFromImage(document, qrImage(content.qrCode())),
                50,
                450,
                180,
                180);
          }
        }
      }
      document.save(out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static BufferedImage qrImage(String text) {
    BitMatrix matrix;
    try {
      matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 300, 300);
    } catch (WriterException e) {
      throw new IllegalArgumentException(e);
    }
    BufferedImage image =
        new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_INT_RGB);
    for (int x = 0; x < matrix.getWidth(); x++) {
      for (int y = 0; y < matrix.getHeight(); y++) {
        image.setRGB(x, y, matrix.get(x, y) ? 0x000000 : 0xFFFFFF);
      }
    }
    return image;
  }
}

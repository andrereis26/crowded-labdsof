package com.crowded.core.ticket.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.crowded.core.ticket.TicketPdfs;
import java.nio.charset.StandardCharsets;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;

class PdfTicketFileReaderTest {

  private final PdfTicketFileReader reader = new PdfTicketFileReader();

  @Test
  void readsTheQrCodeAndTheText() {
    assertThat(reader.read(TicketPdfs.ticket("QR-ENTRY-0001")))
        .hasValueSatisfying(
            ticket -> {
              assertThat(ticket.entryCode()).isEqualTo("QR-ENTRY-0001");
              assertThat(ticket.text()).contains("Código do evento: MARG-2026", "Lugar 14");
            });
  }

  @Test
  void findsTheQrCodeOnALaterPage() {
    assertThat(reader.read(TicketPdfs.ticketWithQrOnPage(3, "QR-ENTRY-0002")))
        .hasValueSatisfying(ticket -> assertThat(ticket.entryCode()).isEqualTo("QR-ENTRY-0002"));
  }

  @Test
  void looksOnlyAtTheFirstPages() {
    byte[] pdf = TicketPdfs.ticketWithQrOnPage(PdfTicketFileReader.MAX_PAGES + 1, "QR-ENTRY-0003");

    assertThat(reader.read(pdf)).isEmpty();
  }

  @Test
  void findsNothingWithoutAQrCode() {
    assertThat(reader.read(TicketPdfs.withoutQrCode())).isEmpty();
  }

  @Test
  void rendersAPrintedTicketAtFullResolution() {
    assertThat(PdfTicketFileReader.renderDpi(PDRectangle.A4)).isEqualTo(200f);
  }

  @Test
  void lowersTheResolutionOfAHugePageToStayWithinThePixelLimit() {
    // 200 inches square: at 200 dpi it would have 1.6 billion pixels.
    PDRectangle huge = new PDRectangle(200 * 72, 200 * 72);

    float dpi = PdfTicketFileReader.renderDpi(huge);

    assertThat(dpi).isLessThan(200f);
    assertThat((double) (200 * dpi) * (200 * dpi))
        .isLessThanOrEqualTo(PdfTicketFileReader.MAX_PIXELS_PER_PAGE * 1.001);
  }

  @Test
  void readsTheQrCodeOfAPageLargerThanTheLimit() {
    assertThat(
            reader.read(TicketPdfs.ticketOnPageOfSize(new PDRectangle(60 * 72, 60 * 72), "QR-BIG")))
        .hasValueSatisfying(ticket -> assertThat(ticket.entryCode()).isEqualTo("QR-BIG"));
  }

  @Test
  void findsNothingInAFileThatIsNotAPdf() {
    assertThat(reader.read("not a pdf".getBytes(StandardCharsets.UTF_8))).isEmpty();
    assertThat(reader.read(new byte[0])).isEmpty();
  }
}

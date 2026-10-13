package ml.dibidani.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import ml.dibidani.common.NotFoundException;
import ml.dibidani.config.AppProperties;
import ml.dibidani.repository.CustomerOrderRepository;
import ml.dibidani.domain.CustomerOrder;
import ml.dibidani.domain.DeliveryMethod;
import ml.dibidani.domain.OrderItem;
import ml.dibidani.domain.PaymentStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Facture PDF aux couleurs Dibidani, avec le QR code de suivi public. */
@Service
public class InvoiceService {
  private static final Color OLIVE = new Color(0x3F, 0x83, 0x17);
  private static final Color PETROL = new Color(0x0E, 0x4C, 0x43);
  private static final Color ORANGE = new Color(0xFD, 0x8D, 0x0B);
  private static final Color TEXT = new Color(0x08, 0x21, 0x1E);
  private static final Color MUTED = new Color(0x5B, 0x6B, 0x68);
  private static final Color LINE = new Color(0xE3, 0xEA, 0xE2);
  private static final Color SOFT = new Color(0xF3, 0xF8, 0xEF);
  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Africa/Bamako"));

  private final ShopService shop;
  private final QrCodeService qr;
  private final AppProperties app;
  private final CustomerOrderRepository orders;

  public InvoiceService(ShopService shop, QrCodeService qr, AppProperties app, CustomerOrderRepository orders) {
    this.shop = shop;
    this.qr = qr;
    this.app = app;
    this.orders = orders;
  }

  @Transactional(readOnly = true)
  public byte[] pdf(Long orderId) {
    CustomerOrder o = orders.findById(orderId).orElseThrow(() -> new NotFoundException("Commande introuvable"));
    var out = new ByteArrayOutputStream();
    var doc = new Document(PageSize.A4, 40, 40, 40, 50);
    PdfWriter.getInstance(doc, out);
    doc.addTitle("Facture " + o.getOrderNumber());
    doc.addAuthor(shop.name());
    doc.open();
    try {
      header(doc, o);
      parties(doc, o);
      items(doc, o);
      totals(doc, o);
      footer(doc);
    } catch (DocumentException | IOException e) {
      throw new IllegalStateException("Facture impossible à générer", e);
    } finally {
      doc.close();
    }
    return out.toByteArray();
  }

  private void header(Document doc, CustomerOrder o) throws DocumentException, IOException {
    var table = new PdfPTable(new float[]{3, 2});
    table.setWidthPercentage(100);
    var left = cell();
    left.addElement(new Paragraph(shop.name().toUpperCase(Locale.ROOT), font(22, Font.BOLD, OLIVE)));
    String slogan = shop.get("slogan");
    if (slogan != null) left.addElement(new Paragraph(slogan, font(10, Font.ITALIC, ORANGE)));
    left.addElement(spacer(6));
    for (String line : new String[]{shop.get("address"), shop.get("city"), shop.get("phone"), shop.get("email")}) {
      if (line != null) left.addElement(new Paragraph(line, font(9, Font.NORMAL, MUTED)));
    }
    table.addCell(left);

    var right = cell();
    right.setHorizontalAlignment(Element.ALIGN_RIGHT);
    var title = new Paragraph("FACTURE", font(18, Font.BOLD, PETROL));
    title.setAlignment(Element.ALIGN_RIGHT);
    right.addElement(title);
    right.addElement(rightLine("N° " + o.getOrderNumber(), font(10, Font.BOLD, TEXT)));
    right.addElement(rightLine("Date : " + DATE.format(o.getCreatedAt()), font(9, Font.NORMAL, MUTED)));
    right.addElement(rightLine("Statut : " + o.getStatus().label(), font(9, Font.NORMAL, MUTED)));
    right.addElement(rightLine("Paiement : " + (o.getPaymentStatus() == PaymentStatus.SUCCESS ? "réglé" : "à régler"), font(9, Font.BOLD,
        o.getPaymentStatus() == PaymentStatus.SUCCESS ? OLIVE : ORANGE)));
    Image code = Image.getInstance(qr.png(app.trackingUrl(o.getTrackingToken()), 300));
    code.scaleAbsolute(78, 78);
    code.setAlignment(Image.ALIGN_RIGHT);
    right.addElement(code);
    right.addElement(rightLine("Scannez pour suivre la commande", font(7, Font.NORMAL, MUTED)));
    table.addCell(right);
    doc.add(table);
    doc.add(spacer(12));
  }

  private void parties(Document doc, CustomerOrder o) throws DocumentException {
    var table = new PdfPTable(2);
    table.setWidthPercentage(100);
    var client = boxed("Client");
    client.addElement(new Paragraph(o.getCustomerName() == null ? "-" : o.getCustomerName(), font(10, Font.BOLD, TEXT)));
    if (o.getCustomerPhone() != null) client.addElement(new Paragraph(o.getCustomerPhone(), font(9, Font.NORMAL, MUTED)));
    if (o.getCustomerEmail() != null) client.addElement(new Paragraph(o.getCustomerEmail(), font(9, Font.NORMAL, MUTED)));
    table.addCell(client);

    var delivery = boxed(o.getDeliveryMethod() == DeliveryMethod.PICKUP ? "Retrait" : "Livraison");
    delivery.addElement(new Paragraph(o.getDeliveryMethod().label(), font(10, Font.BOLD, TEXT)));
    if (o.getDeliveryAddress() != null) delivery.addElement(new Paragraph(o.getDeliveryAddress(), font(9, Font.NORMAL, MUTED)));
    String zone = o.getDeliveryZoneName() != null ? o.getDeliveryZoneName() : o.getDeliveryDistrict();
    if (zone != null && o.getDeliveryMethod() != DeliveryMethod.PICKUP) delivery.addElement(new Paragraph(zone + (o.getDeliveryCity() == null ? "" : ", " + o.getDeliveryCity()), font(9, Font.NORMAL, MUTED)));
    String method = OrderMapper.methodLabel(o);
    if (method != null) delivery.addElement(new Paragraph("Paiement : " + method, font(9, Font.NORMAL, MUTED)));
    table.addCell(delivery);
    doc.add(table);
    doc.add(spacer(14));
  }

  private void items(Document doc, CustomerOrder o) throws DocumentException {
    var table = new PdfPTable(new float[]{4.2f, 1.6f, 0.8f, 1.6f, 1.7f});
    table.setWidthPercentage(100);
    table.setHeaderRows(1);
    for (String h : new String[]{"Article", "Vendeur", "Qté", "Prix unitaire", "Total"}) {
      var c = new PdfPCell(new Phrase(h, font(9, Font.BOLD, Color.WHITE)));
      c.setBackgroundColor(PETROL);
      c.setPadding(6);
      c.setBorder(Rectangle.NO_BORDER);
      if (!h.equals("Article") && !h.equals("Vendeur")) c.setHorizontalAlignment(Element.ALIGN_RIGHT);
      table.addCell(c);
    }
    boolean odd = false;
    for (OrderItem i : o.getItems()) {
      Color bg = odd ? SOFT : Color.WHITE;
      odd = !odd;
      var name = new Paragraph(i.getProductName() + (i.getVariantName() == null ? "" : " - " + i.getVariantName()), font(9, Font.BOLD, TEXT));
      var article = row(bg, Element.ALIGN_LEFT);
      article.addElement(name);
      article.addElement(new Paragraph("Réf. " + i.getProductSku(), font(7, Font.NORMAL, MUTED)));
      table.addCell(article);
      table.addCell(text(i.getVendorName() == null ? "-" : i.getVendorName(), bg, Element.ALIGN_LEFT));
      table.addCell(text(String.valueOf(i.getQuantity()), bg, Element.ALIGN_RIGHT));
      table.addCell(text(fcfa(i.getUnitPrice()), bg, Element.ALIGN_RIGHT));
      table.addCell(text(fcfa(i.getLineTotal()), bg, Element.ALIGN_RIGHT));
    }
    doc.add(table);
    doc.add(spacer(10));
  }

  private void totals(Document doc, CustomerOrder o) throws DocumentException {
    var table = new PdfPTable(new float[]{2, 1});
    table.setWidthPercentage(48);
    table.setHorizontalAlignment(Element.ALIGN_RIGHT);
    total(table, "Sous-total", fcfa(o.getSubtotal()), false);
    if (o.getDiscountTotal() != null && o.getDiscountTotal().signum() > 0) {
      total(table, "Remise" + (o.getPromoCode() == null ? "" : " (" + o.getPromoCode() + ")"), "- " + fcfa(o.getDiscountTotal()), false);
    }
    total(table, "Livraison", o.getDeliveryFee().signum() == 0 ? "Offerte" : fcfa(o.getDeliveryFee()), false);
    total(table, "Total TTC", fcfa(o.getTotal()), true);
    doc.add(table);
  }

  private void footer(Document doc) throws DocumentException {
    doc.add(spacer(28));
    var p = new Paragraph("Merci pour votre confiance. " + shop.name() + (shop.get("slogan") == null ? "" : " - " + shop.get("slogan")), font(9, Font.ITALIC, MUTED));
    p.setAlignment(Element.ALIGN_CENTER);
    doc.add(p);
  }

  private static void total(PdfPTable t, String label, String value, boolean strong) {
    Font f = strong ? font(12, Font.BOLD, Color.WHITE) : font(9, Font.NORMAL, TEXT);
    var l = new PdfPCell(new Phrase(label, f));
    var v = new PdfPCell(new Phrase(value, f));
    for (PdfPCell c : new PdfPCell[]{l, v}) {
      c.setPadding(strong ? 7 : 4);
      c.setBorder(strong ? Rectangle.NO_BORDER : Rectangle.BOTTOM);
      c.setBorderColor(LINE);
      if (strong) c.setBackgroundColor(OLIVE);
    }
    v.setHorizontalAlignment(Element.ALIGN_RIGHT);
    t.addCell(l);
    t.addCell(v);
  }

  private static PdfPCell boxed(String title) {
    var c = new PdfPCell();
    c.setBorderColor(LINE);
    c.setPadding(10);
    c.addElement(new Paragraph(title.toUpperCase(Locale.ROOT), font(8, Font.BOLD, OLIVE)));
    return c;
  }

  private static PdfPCell row(Color bg, int align) {
    var c = new PdfPCell();
    c.setBackgroundColor(bg);
    c.setBorder(Rectangle.BOTTOM);
    c.setBorderColor(LINE);
    c.setPadding(6);
    c.setHorizontalAlignment(align);
    return c;
  }

  private static PdfPCell text(String value, Color bg, int align) {
    var c = new PdfPCell(new Phrase(value, font(9, Font.NORMAL, TEXT)));
    c.setBackgroundColor(bg);
    c.setBorder(Rectangle.BOTTOM);
    c.setBorderColor(LINE);
    c.setPadding(6);
    c.setHorizontalAlignment(align);
    c.setVerticalAlignment(Element.ALIGN_MIDDLE);
    return c;
  }

  private static PdfPCell cell() {
    var c = new PdfPCell();
    c.setBorder(Rectangle.NO_BORDER);
    return c;
  }

  private static Paragraph rightLine(String s, Font f) {
    var p = new Paragraph(s, f);
    p.setAlignment(Element.ALIGN_RIGHT);
    return p;
  }

  private static Paragraph spacer(float h) {
    var p = new Paragraph(" ");
    p.setSpacingAfter(h);
    p.setLeading(0);
    return p;
  }

  private static Font font(float size, int style, Color color) { return FontFactory.getFont(FontFactory.HELVETICA, "Cp1252", false, size, style, color); }

  private static String fcfa(BigDecimal v) {
    if (v == null) return "-";
    return NumberFormat.getIntegerInstance(Locale.FRANCE).format(v.setScale(0, RoundingMode.HALF_UP)).replace('\u202f', ' ').replace('\u00a0', ' ') + " FCFA";
  }
}

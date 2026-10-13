package ml.dibidani.notify;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OtpNotifier {
  private static final Logger log = LoggerFactory.getLogger(OtpNotifier.class);

  private final SmsGatewayClient sms;
  private final MailSenderService mail;

  public OtpNotifier(SmsGatewayClient sms, MailSenderService mail) {
    this.sms = sms;
    this.mail = mail;
  }

  public record Delivery(boolean emailSent, boolean smsSent, boolean demo) {}

  public Delivery sendResetCode(String email, String phone, String fullName, String code) {
    boolean demo = (!mail.configured() && mail.mockEnabled()) || (!sms.configured() && sms.mockEnabled());
    boolean emailSent = sendEmail(email, fullName, code);
    boolean smsSent = sendSms(phone, code);
    if (demo && !emailSent && !smsSent) {
      log.info("OTP démo (SMS Gateway / mail non configurés) pour {} / {} : {}", email, phone, code);
    }
    return new Delivery(emailSent, smsSent, demo && !emailSent && !smsSent);
  }

  private boolean sendEmail(String email, String fullName, String code) {
    if (email == null || email.isBlank()) return false;
    if (!mail.configured()) {
      if (mail.mockEnabled()) log.info("OTP e-mail [mock] {} : {}", email, code);
      else log.warn("E-mail OTP non envoyé : MAIL_HOST n'est pas configuré");
      return false;
    }
    String name = fullName == null || fullName.isBlank() ? "client" : fullName.trim();
    String html = """
        <div style="font-family:Arial,sans-serif;max-width:480px;color:#173c31">
          <h1 style="color:#087a47;font-size:22px">Dibidani</h1>
          <p>Bonjour %s,</p>
          <p>Voici votre code pour réinitialiser votre mot de passe :</p>
          <p style="font-size:28px;letter-spacing:8px;font-weight:800;color:#087a47">%s</p>
          <p>Ce code est valable 10 minutes. Ne le communiquez à personne.</p>
          <p style="color:#73837d;font-size:13px">Si vous n'êtes pas à l'origine de cette demande, ignorez cet e-mail.</p>
        </div>
        """.formatted(escape(name), code);
    try {
      mail.send(email, "Votre code Dibidani", html);
      return true;
    } catch (RuntimeException e) {
      return false;
    }
  }

  private boolean sendSms(String phone, String code) {
    if (phone == null || phone.isBlank() || Phones.e164(phone) == null) return false;
    if (!sms.configured()) {
      if (sms.mockEnabled()) log.info("OTP SMS [mock / SMS Gateway] {} : {}", Phones.e164(phone), code);
      else log.warn("SMS OTP non envoyé : SMS Gateway n'est pas configuré");
      return false;
    }
    try {
      sms.send(phone, "Dibidani: votre code est " + code + ". Valable 10 min. Ne le partagez pas.");
      return true;
    } catch (RuntimeException e) {
      return false;
    }
  }

  private static String escape(String s) {
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}

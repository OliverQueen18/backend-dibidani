package ml.dibidani.notify;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import ml.dibidani.config.MailProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
public class MailSenderService {
  private static final Logger log = LoggerFactory.getLogger(MailSenderService.class);

  private final MailProperties props;
  private final ObjectProvider<JavaMailSender> mail;

  public MailSenderService(MailProperties props, ObjectProvider<JavaMailSender> mail) {
    this.props = props;
    this.mail = mail;
  }

  public boolean configured() {
    JavaMailSender sender = mail.getIfAvailable();
    return sender != null && hostPresent(sender);
  }

  public boolean mockEnabled() { return props.mockEnabled(); }

  public void send(String to, String subject, String html) {
    JavaMailSender sender = mail.getIfAvailable();
    if (sender == null) throw new IllegalStateException("Le serveur mail n'est pas configuré");
    try {
      MimeMessage message = sender.createMimeMessage();
      var helper = new MimeMessageHelper(message, "UTF-8");
      helper.setFrom(new InternetAddress(props.sender(), props.senderName(), "UTF-8"));
      helper.setTo(to);
      helper.setSubject(subject);
      helper.setText(html, true);
      sender.send(message);
    } catch (Exception e) {
      log.warn("Envoi d'e-mail impossible vers {} : {}", to, e.getMessage());
      throw new IllegalStateException("Envoi d'e-mail impossible");
    }
  }

  private static boolean hostPresent(JavaMailSender sender) {
    try {
      var getHost = sender.getClass().getMethod("getHost");
      Object host = getHost.invoke(sender);
      return host instanceof String s && !s.isBlank();
    } catch (ReflectiveOperationException e) {
      return true;
    }
  }
}

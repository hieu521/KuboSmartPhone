package com.example.notification.service;

import com.example.notification.mq.UserRef;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

@Service
public class MailService {

    private final JavaMailSender mailSender;
    private final String from;

    public MailService(JavaMailSender mailSender,
                        @Value("${app.mail.from:${spring.mail.username:}}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void sendBlackFridayMail(UserRef user, String campaignId) {
        if (from == null || from.isBlank()) {
            throw new IllegalStateException("Missing mail from address (set app.mail.from or spring.mail.username)");
        }
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            return;
        }

        String subject = "Black Friday - Uu dai dac biet cho ban!";
        String campaignSafe = escape(campaignId);
        String html = """
                <!doctype html>
                <html>
                <head>
                  <meta charset="UTF-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                  <title>Black Friday</title>
                </head>
                <body style="margin:0;padding:0;background:#f3f4f6;font-family:Arial,Helvetica,sans-serif;color:#111827;">
                  <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" style="background:#f3f4f6;padding:24px 12px;">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="640" cellspacing="0" cellpadding="0" style="max-width:640px;width:100%%;background:#ffffff;border-radius:14px;overflow:hidden;">
                          <tr>
                            <td style="background:linear-gradient(135deg,#0f172a,#1e293b);padding:28px 24px;">
                              <h1 style="margin:0;color:#ffffff;font-size:24px;line-height:1.3;">Black Friday Mega Deals</h1>
                              <p style="margin:10px 0 0;color:#cbd5e1;font-size:14px;">Uu dai co han - dung bo lo!</p>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:24px;">
                              <p style="margin:0 0 14px;font-size:15px;line-height:1.6;">Xin chao,</p>
                              <p style="margin:0 0 14px;font-size:15px;line-height:1.6;">
                                Chien dich khuyen mai cua ban da san sang. Ma campaign:
                                <span style="display:inline-block;background:#f1f5f9;border:1px solid #e2e8f0;border-radius:8px;padding:4px 10px;font-weight:700;color:#0f172a;">
                                  %s
                                </span>
                              </p>
                              <p style="margin:0 0 22px;font-size:15px;line-height:1.6;">
                                Dang nhap he thong de xem gia uu dai va dat hang ngay khi deal con hieu luc.
                              </p>
                              <a href="http://localhost:8080" style="display:inline-block;background:#2563eb;color:#ffffff;text-decoration:none;font-weight:700;padding:12px 18px;border-radius:10px;">
                                Xem uu dai ngay
                              </a>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:18px 24px;background:#f8fafc;border-top:1px solid #e5e7eb;">
                              <p style="margin:0;font-size:12px;color:#64748b;line-height:1.6;">
                                Ban nhan duoc email nay vi dang ky thong bao khuyen mai tu he thong.<br/>
                                Neu khong phai ban, vui long bo qua email nay.
                              </p>
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </body>
                </html>
                """.formatted(campaignSafe);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(user.getEmail());
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to send mail", e);
        }
    }

    // Chống lỗi template đơn giản (demo)
    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}


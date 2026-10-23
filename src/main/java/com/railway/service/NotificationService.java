package com.railway.service;

import com.azure.communication.email.EmailClient;
import com.azure.communication.email.models.EmailMessage;
import com.azure.communication.sms.SmsClient;
import com.railway.document.NotificationLogDocument;
import com.railway.document.UserDocument;
import com.railway.repository.Repos.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final ObjectProvider<SmsClient> sms;
    private final ObjectProvider<EmailClient> email;
    private final UserRepo users;
    private final NotificationLogRepo logs;
    private final String smsFrom, emailFrom;

    public NotificationService(ObjectProvider<SmsClient> sms, ObjectProvider<EmailClient> email, UserRepo users,
                               NotificationLogRepo logs, @Value("${acs.sms-from:}") String smsFrom,
                               @Value("${acs.email-from:}") String emailFrom) {
        this.sms = sms; this.email = email; this.users = users; this.logs = logs;
        this.smsFrom = smsFrom; this.emailFrom = emailFrom;
    }

    public void notify(String userId, String subject, String message) {
        UserDocument u = users.findByUserId(userId).orElse(null);
        if (u == null) { log.warn("No user {} to notify", userId); return; }
        send("SMS", u, u.phone, subject, message);
        send("EMAIL", u, u.email, subject, message);
    }

    private void send(String channel, UserDocument u, String to, String subject, String message) {
        String status = "MOCK";
        try {
            if (channel.equals("SMS") && sms.getIfAvailable() != null && !smsFrom.isBlank()) {
                sms.getIfAvailable().send(smsFrom, to, message);
                status = "SENT";
            } else if (channel.equals("EMAIL") && email.getIfAvailable() != null && !emailFrom.isBlank()) {
                email.getIfAvailable().beginSend(new EmailMessage().setSenderAddress(emailFrom)
                        .setToRecipients(to).setSubject(subject).setBodyPlainText(message));
                status = "SENT";
            } else {
                log.info("[MOCK {}] to={} subject={} msg={}", channel, to, subject, message);
            }
        } catch (Exception e) {
            status = "FAILED";
            log.error("{} send failed: {}", channel, e.getMessage());
        }
        NotificationLogDocument d = new NotificationLogDocument();
        d.userId = u.userId; d.channel = channel; d.recipient = to; d.subject = subject; d.message = message; d.status = status;
        logs.save(d);
    }
}

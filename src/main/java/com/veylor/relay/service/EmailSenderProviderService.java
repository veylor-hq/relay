package com.veylor.relay.service;

import com.veylor.relay.entity.EmailSender;
import com.veylor.relay.repository.EmailSenderRepository;
import com.veylor.relay.util.EncryptionUtils;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailSenderProviderService {

    private final EmailSenderRepository emailSenderRepository;
    private final JavaMailSender defaultMailSender;

    @Value("${app.security.master-key:veylor-relay-default-master-key-32b}")
    private String masterKey;

    @Value("${spring.mail.from-address:contact@relay.dev}")
    private String defaultFromAddress;

    private final Map<UUID, JavaMailSender> senderClientCache = new ConcurrentHashMap<>();

    public record ResolvedSenderClient(JavaMailSender mailSender, String fromAddress) {
    }

    public ResolvedSenderClient getSenderClient(EmailSender sender) {
        if (sender == null) {
            return new ResolvedSenderClient(defaultMailSender, defaultFromAddress);
        }

        JavaMailSender client = senderClientCache.computeIfAbsent(sender.getId(), id -> buildJavaMailSender(sender));
        return new ResolvedSenderClient(client, sender.getFromAddress());
    }

    public void evictSenderCache(UUID senderId) {
        senderClientCache.remove(senderId);
    }

    private JavaMailSender buildJavaMailSender(EmailSender sender) {
        JavaMailSenderImpl impl = new JavaMailSenderImpl();
        impl.setHost(sender.getSmtpHost());
        impl.setPort(sender.getSmtpPort());

        if (sender.getUsername() != null && !sender.getUsername().isBlank()) {
            impl.setUsername(sender.getUsername());
        }

        if (sender.getEncryptedPassword() != null && !sender.getEncryptedPassword().isBlank()) {
            String decryptedPassword = EncryptionUtils.decrypt(sender.getEncryptedPassword(), masterKey);
            impl.setPassword(decryptedPassword);
        }

        Properties props = impl.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", String.valueOf(Boolean.TRUE.equals(sender.getAuthEnabled())));
        props.put("mail.smtp.starttls.enable", String.valueOf(Boolean.TRUE.equals(sender.getStarttlsEnabled())));
        props.put("mail.smtp.starttls.required", String.valueOf(Boolean.TRUE.equals(sender.getStarttlsRequired())));
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "5000");
        props.put("mail.smtp.writetimeout", "5000");

        return impl;
    }
}

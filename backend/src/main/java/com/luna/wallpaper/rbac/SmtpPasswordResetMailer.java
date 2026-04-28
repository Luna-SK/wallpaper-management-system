package com.luna.wallpaper.rbac;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import com.luna.wallpaper.config.MailProperties;

@Service
class SmtpPasswordResetMailer extends PasswordResetMailer {

	private static final Logger log = LoggerFactory.getLogger(SmtpPasswordResetMailer.class);

	private final MailProperties properties;

	SmtpPasswordResetMailer(MailProperties properties) {
		this.properties = properties;
	}

	@Override
	public void send(AppUser user, String token, Instant expiresAt) {
		String link = resetLink(token);
		if (!properties.safeEnabled()) {
			log.warn("password reset mail transport disabled, userId={}", user.id());
			throw new PasswordResetMailException("邮件服务暂不可用，请联系管理员");
		}
		try {
			JavaMailSenderImpl sender = mailSender();
			SimpleMailMessage message = new SimpleMailMessage();
			message.setFrom(properties.safeFrom());
			message.setTo(user.email());
			message.setSubject("图片管理系统密码重置");
			message.setText("""
					%s，您好：

					您正在为账号 %s 重置图片管理系统密码，请点击以下链接继续：
					%s

					该链接将在 %s 过期，且只能使用一次。若不是您本人操作，请忽略此邮件。
					""".formatted(user.displayName(), user.username(), link, expiresAt));
			sender.send(message);
		}
		catch (RuntimeException ex) {
			log.error("failed to send password reset mail, userId={}", user.id(), ex);
			throw new PasswordResetMailException("邮件服务暂不可用，请联系管理员", ex);
		}
	}

	private String resetLink(String token) {
		return properties.safeFrontendBaseUrl() + "/reset-password?token="
				+ URLEncoder.encode(token, StandardCharsets.UTF_8);
	}

	private JavaMailSenderImpl mailSender() {
		JavaMailSenderImpl sender = new JavaMailSenderImpl();
		sender.setHost(properties.safeHost());
		sender.setPort(properties.safePort());
		if (properties.hasUsername()) {
			sender.setUsername(properties.username().trim());
			sender.setPassword(properties.password());
		}
		Properties javaMailProperties = sender.getJavaMailProperties();
		javaMailProperties.put("mail.smtp.auth", Boolean.toString(properties.safeSmtpAuth()));
		javaMailProperties.put("mail.smtp.starttls.enable", Boolean.toString(properties.safeSmtpStarttls()));
		return sender;
	}
}

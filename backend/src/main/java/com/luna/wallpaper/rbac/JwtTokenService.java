package com.luna.wallpaper.rbac;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Service;

import com.luna.wallpaper.config.SecurityProperties;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
class JwtTokenService {

	private static final String HMAC_ALGORITHM = "HmacSHA256";
	private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
	private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
	private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
	};

	private final ObjectMapper objectMapper;
	private final byte[] secret;

	JwtTokenService(ObjectMapper objectMapper, SecurityProperties properties) {
		this.objectMapper = objectMapper;
		this.secret = properties.requiredJwtSecret().getBytes(StandardCharsets.UTF_8);
	}

	String createAccessToken(AppUser user, String sessionId, Instant expiresAt) {
		try {
			String header = encodeJson(Map.of("alg", "HS256", "typ", "JWT"));
			String payload = encodeJson(Map.of(
					"sub", user.id(),
					"sid", sessionId,
					"jti", UUID.randomUUID().toString(),
					"username", user.username(),
					"iat", Instant.now().getEpochSecond(),
					"exp", expiresAt.getEpochSecond()));
			String signingInput = header + "." + payload;
			return signingInput + "." + sign(signingInput);
		}
		catch (Exception ex) {
			throw new IllegalStateException("failed to create access token", ex);
		}
	}

	AccessTokenClaims parse(String token) {
		try {
			String[] parts = token == null ? new String[0] : token.split("\\.");
			if (parts.length != 3) {
				throw new IllegalArgumentException("invalid token format");
			}
			String signingInput = parts[0] + "." + parts[1];
			if (!MessageDigest.isEqual(sign(signingInput).getBytes(StandardCharsets.UTF_8),
					parts[2].getBytes(StandardCharsets.UTF_8))) {
				throw new IllegalArgumentException("invalid token signature");
			}
			Map<String, Object> payload = objectMapper.readValue(URL_DECODER.decode(parts[1]), MAP_TYPE);
			String userId = stringClaim(payload, "sub");
			String sessionId = stringClaim(payload, "sid");
			long expiresAt = numericClaim(payload, "exp");
			if (Instant.ofEpochSecond(expiresAt).isBefore(Instant.now())) {
				throw new IllegalArgumentException("access token expired");
			}
			return new AccessTokenClaims(userId, sessionId, Instant.ofEpochSecond(expiresAt));
		}
		catch (Exception ex) {
			throw new IllegalArgumentException("invalid access token", ex);
		}
	}

	private String encodeJson(Map<String, Object> value) throws Exception {
		return URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
	}

	private String sign(String value) throws GeneralSecurityException {
		Mac mac = Mac.getInstance(HMAC_ALGORITHM);
		mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
		return URL_ENCODER.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
	}

	private String stringClaim(Map<String, Object> payload, String key) {
		Object value = payload.get(key);
		if (value instanceof String text && !text.isBlank()) {
			return text;
		}
		throw new IllegalArgumentException("missing claim " + key);
	}

	private long numericClaim(Map<String, Object> payload, String key) {
		Object value = payload.get(key);
		if (value instanceof Number number) {
			return number.longValue();
		}
		throw new IllegalArgumentException("missing claim " + key);
	}

	record AccessTokenClaims(String userId, String sessionId, Instant expiresAt) {
	}
}

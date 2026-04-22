package com.bodrumaquapark.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.bodrumaquapark.config.JwtProperties;
import com.bodrumaquapark.entity.RoleCode;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

	private final JwtProperties jwtProperties;

	public JwtService(JwtProperties jwtProperties) {
		this.jwtProperties = jwtProperties;
	}

	public String createToken(String userId, RoleCode role, Collection<String> saleAreaCodes,
			boolean ticketSalesAllowed, boolean balanceLoadAllowed, boolean adminPanelAccess) {
		Instant now = Instant.now();
		Instant exp = now.plusSeconds(jwtProperties.getExpirationHours() * 3600);
		String areasCsv = "";
		if (saleAreaCodes != null && !saleAreaCodes.isEmpty()) {
			areasCsv = saleAreaCodes.stream().map(String::trim).filter(s -> !s.isEmpty()).sorted()
					.collect(Collectors.joining(","));
		}
		return Jwts.builder()
				.subject(userId)
				.claim("role", role.name())
				.claim("areas", areasCsv)
				.claim("ticket", ticketSalesAllowed)
				.claim("balance", balanceLoadAllowed)
				.claim("adminPanel", adminPanelAccess)
				.issuedAt(Date.from(now))
				.expiration(Date.from(exp))
				.signWith(signingKey())
				.compact();
	}

	public Claims parseAndValidate(String token) {
		try {
			return Jwts.parser()
					.verifyWith(signingKey())
					.build()
					.parseSignedClaims(token)
					.getPayload();
		} catch (ExpiredJwtException e) {
			throw new InvalidTokenException("Oturum süresi doldu");
		} catch (JwtException e) {
			throw new InvalidTokenException("Geçersiz oturum");
		}
	}

	public RoleCode readRole(Claims claims) {
		Object v = claims.get("role");
		if (v == null) {
			return RoleCode.CASHIER;
		}
		String r = v instanceof String s ? s : String.valueOf(v);
		r = r != null ? r.trim() : "";
		if (r.isEmpty()) {
			return RoleCode.CASHIER;
		}
		try {
			return RoleCode.valueOf(r);
		} catch (IllegalArgumentException e) {
			return RoleCode.CASHIER;
		}
	}

	/**
	 * JWT claim boolean — eski/uyumsuz tokenlarda string veya sayı olabilir; {@code Boolean.class} ile okumak
	 * ClassCastException verebilir.
	 */
	public boolean readBooleanClaim(Claims claims, String key, boolean defaultIfNullOrUnknown) {
		Object v = claims.get(key);
		if (v == null) {
			return defaultIfNullOrUnknown;
		}
		if (v instanceof Boolean b) {
			return b;
		}
		if (v instanceof String s) {
			return "true".equalsIgnoreCase(s) || "1".equals(s);
		}
		if (v instanceof Number n) {
			return n.intValue() != 0;
		}
		return defaultIfNullOrUnknown;
	}

	/** JWT’deki satış alanı kodları (virgülle ayrılmış) */
	public boolean readAdminPanelAccess(Claims claims) {
		return readBooleanClaim(claims, "adminPanel", false);
	}

	public Set<String> readSaleAreaCodes(Claims claims) {
		Object v = claims.get("areas");
		if (v == null) {
			return Set.of();
		}
		Set<String> out = new LinkedHashSet<>();
		if (v instanceof String csv) {
			if (csv.isBlank()) {
				return Set.of();
			}
			for (String p : csv.split(",")) {
				String s = p.trim();
				if (!s.isEmpty()) {
					out.add(s);
				}
			}
			return out;
		}
		if (v instanceof Collection<?> c) {
			for (Object o : c) {
				if (o != null) {
					String s = String.valueOf(o).trim();
					if (!s.isEmpty()) {
						out.add(s);
					}
				}
			}
			return out;
		}
		if (v instanceof Object[] arr) {
			for (Object o : arr) {
				if (o != null) {
					String s = String.valueOf(o).trim();
					if (!s.isEmpty()) {
						out.add(s);
					}
				}
			}
			return out;
		}
		String single = String.valueOf(v).trim();
		if (!single.isEmpty()) {
			out.add(single);
		}
		return out;
	}

	private SecretKey signingKey() {
		byte[] bytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
		if (bytes.length < 32) {
			throw new IllegalStateException("app.jwt.secret en az 32 karakter olmalıdır");
		}
		return Keys.hmacShaKeyFor(bytes);
	}
}

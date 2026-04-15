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
			boolean ticketSalesAllowed, boolean balanceLoadAllowed) {
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
		String r = claims.get("role", String.class);
		if (r == null) {
			return RoleCode.CASHIER;
		}
		return RoleCode.valueOf(r);
	}

	/** JWT’deki satış alanı kodları (virgülle ayrılmış) */
	public Set<String> readSaleAreaCodes(Claims claims) {
		String csv = claims.get("areas", String.class);
		if (csv == null || csv.isBlank()) {
			return Set.of();
		}
		Set<String> out = new LinkedHashSet<>();
		for (String p : csv.split(",")) {
			String s = p.trim();
			if (!s.isEmpty()) {
				out.add(s);
			}
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

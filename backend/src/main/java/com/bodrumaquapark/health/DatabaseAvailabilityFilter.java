package com.bodrumaquapark.health;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Veritabanı erişilemiyorsa {@code /api/**} çağrılarını {@code 503 Service Unavailable} ile reddeder.
 * Statik dosyalar (login, pos vb.), health ve info uçları açık kalır; uygulama ayağa kalkmaya devam eder.
 *
 * <p>Kapatmak için: {@code APP_DB_GUARD_ENABLED=false}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class DatabaseAvailabilityFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(DatabaseAvailabilityFilter.class);

	private final DatabaseAvailabilityService availability;
	private final boolean enabled;

	public DatabaseAvailabilityFilter(DatabaseAvailabilityService availability,
			@Value("${app.startup.db-guard.enabled:true}") boolean enabled) {
		this.availability = availability;
		this.enabled = enabled;
	}

	@Override
	protected void doFilterInternal(
			@NonNull HttpServletRequest request,
			@NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain) throws ServletException, IOException {

		if (!enabled) {
			filterChain.doFilter(request, response);
			return;
		}

		String path = stripContext(request);
		if (!path.startsWith("/api/") || isAlwaysAllowed(path)) {
			filterChain.doFilter(request, response);
			return;
		}

		if (availability.isAvailable()) {
			filterChain.doFilter(request, response);
			return;
		}

		log.debug("db-guard: 503 — path={}", path);
		response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
		response.setHeader("Retry-After", "5");
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		// 'error' alanı: kullanıcıya gösterilen Türkçe metin (frontend ile sözleşme).
		// 'code' alanı: makina-okur kodu (otomasyon / log filtresi için).
		response.getWriter().write(
				"{\"type\":\"about:blank\",\"title\":\"Service Unavailable\",\"status\":503,"
						+ "\"error\":\"Veritabanına şu an erişilemiyor. İşlem alınamıyor; bağlantı geri geldiğinde otomatik devam edecek.\","
						+ "\"code\":\"database_unavailable\","
						+ "\"detail\":\"Veritabanına şu an erişilemiyor. İşlem alınamıyor; bağlantı geri geldiğinde otomatik devam edecek.\"}");
	}

	private static String stripContext(HttpServletRequest request) {
		String path = request.getRequestURI();
		String context = request.getContextPath();
		if (context != null && !context.isEmpty() && path.startsWith(context)) {
			return path.substring(context.length());
		}
		return path;
	}

	/** DB gerektirmeyen / her zaman erişilebilmesi gereken uçlar. */
	private static boolean isAlwaysAllowed(String path) {
		return path.equals("/api/health")
				|| path.equals("/api/info")
				|| path.startsWith("/api/printer/status")
				|| path.startsWith("/actuator/");
	}
}

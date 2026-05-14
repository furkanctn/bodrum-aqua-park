package com.bodrumaquapark.access;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.bodrumaquapark.config.AccessGatewayProperties;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * POST /api/access/check için basit IP başına oran sınırı (bellek içi, tek düğüm).
 * Çoklu düğüm için API Gateway veya Redis tabanlı limiter önerilir.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
public class AccessCheckRateLimitFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(AccessCheckRateLimitFilter.class);

	private final AccessGatewayProperties gatewayProperties;
	private final ConcurrentHashMap<String, Deque<Long>> hitsByIp = new ConcurrentHashMap<>();

	public AccessCheckRateLimitFilter(AccessGatewayProperties gatewayProperties) {
		this.gatewayProperties = gatewayProperties;
	}

	@Override
	protected void doFilterInternal(
			@NonNull HttpServletRequest request,
			@NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain)
			throws ServletException, java.io.IOException {

		if (!"POST".equalsIgnoreCase(request.getMethod())) {
			filterChain.doFilter(request, response);
			return;
		}
		String path = request.getRequestURI();
		String context = request.getContextPath();
		if (context != null && !context.isEmpty() && path.startsWith(context)) {
			path = path.substring(context.length());
		}
		if (!"/api/access/check".equals(path)) {
			filterChain.doFilter(request, response);
			return;
		}

		int limit = gatewayProperties.getRateLimitRequestsPerMinute();
		if (limit <= 0) {
			filterChain.doFilter(request, response);
			return;
		}

		String ip = ClientAccessMeta.from(request).clientIp();
		if (ip.isEmpty()) {
			ip = "unknown";
		}
		long now = System.currentTimeMillis();
		long windowMs = 60_000L;
		Deque<Long> window = hitsByIp.computeIfAbsent(ip, k -> new ArrayDeque<>());
		synchronized (window) {
			while (!window.isEmpty() && now - window.peekFirst() > windowMs) {
				window.pollFirst();
			}
			if (window.size() >= limit) {
				log.warn("access.rate-limit ip={} path=/api/access/check", ip);
				response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
				response.setContentType(MediaType.APPLICATION_JSON_VALUE);
				response.getWriter().write("{\"error\":\"Çok fazla istek\",\"detail\":\"Dakika başına istek sınırı aşıldı\"}");
				return;
			}
			window.addLast(now);
		}

		filterChain.doFilter(request, response);
	}
}

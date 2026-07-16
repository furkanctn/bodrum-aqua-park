package com.bodrumaquapark.config;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Admin / POS statik sayfalarında Edge’in eski HTML/JS tutmasını azaltır (çoklu kasa güncellemesi).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class NoCacheStaticPagesFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String path = request.getRequestURI();
		if (path != null && shouldDisableCache(path)) {
			response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
			response.setHeader("Pragma", "no-cache");
			response.setHeader("Expires", "0");
		}
		filterChain.doFilter(request, response);
	}

	private static boolean shouldDisableCache(String path) {
		return path.equals("/admin")
				|| path.equals("/admin.html")
				|| path.equals("/pos.html")
				|| path.equals("/index.html")
				|| path.startsWith("/js/admin.js")
				|| path.startsWith("/js/pos.js")
				|| path.startsWith("/css/admin.css");
	}
}

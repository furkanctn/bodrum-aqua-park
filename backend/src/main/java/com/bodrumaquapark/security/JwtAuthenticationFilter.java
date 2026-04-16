package com.bodrumaquapark.security;

import java.io.IOException;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.bodrumaquapark.entity.RoleCode;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * /api/* isteklerinde Bearer JWT doğrular; giriş ve sağlık uçları hariç.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

	public static final String ATTR_USER_ID = "authUserId";
	public static final String ATTR_ROLE = "authRole";
	public static final String ATTR_SALE_AREA_CODES = "authSaleAreaCodes";
	/** JWT claim "balance" (bakiye yükleme); yoksa true kabul (eski token uyumu) */
	public static final String ATTR_BALANCE_LOAD_ALLOWED = "authBalanceLoadAllowed";

	/** JWT claim "ticket" — POS bilet / yaş grubu satışı */
	public static final String ATTR_TICKET_SALES_ALLOWED = "authTicketSalesAllowed";

	/** JWT claim "adminPanel" — kasiyer/süpervizör için yönetim paneli (/api/admin/menu-pages vb.) */
	public static final String ATTR_ADMIN_PANEL_ACCESS = "authAdminPanelAccess";

	private final JwtService jwtService;

	public JwtAuthenticationFilter(JwtService jwtService) {
		this.jwtService = jwtService;
	}

	@Override
	protected void doFilterInternal(
			@NonNull HttpServletRequest request,
			@NonNull HttpServletResponse response,
			@NonNull FilterChain filterChain)
			throws ServletException, IOException {

		String path = request.getRequestURI();
		String context = request.getContextPath();
		if (context != null && !context.isEmpty() && path.startsWith(context)) {
			path = path.substring(context.length());
		}

		if (!path.startsWith("/api/")) {
			filterChain.doFilter(request, response);
			return;
		}

		if (isPublic(path, request.getMethod())) {
			filterChain.doFilter(request, response);
			return;
		}

		String auth = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (auth == null || !auth.startsWith("Bearer ")) {
			unauthorized(response, "Oturum gerekli");
			return;
		}
		String token = auth.substring(7).trim();
		if (token.isEmpty()) {
			unauthorized(response, "Oturum gerekli");
			return;
		}

		try {
			Claims claims = jwtService.parseAndValidate(token);
			String userId = claims.getSubject();
			RoleCode role = jwtService.readRole(claims);
			Set<String> saleAreas = jwtService.readSaleAreaCodes(claims);
			request.setAttribute(ATTR_USER_ID, userId);
			request.setAttribute(ATTR_ROLE, role);
			request.setAttribute(ATTR_SALE_AREA_CODES, saleAreas);
			Boolean balanceClaim = claims.get("balance", Boolean.class);
			boolean balanceLoadAllowed = balanceClaim == null || Boolean.TRUE.equals(balanceClaim);
			request.setAttribute(ATTR_BALANCE_LOAD_ALLOWED, balanceLoadAllowed);
			Boolean ticketClaim = claims.get("ticket", Boolean.class);
			boolean ticketSalesAllowed = ticketClaim == null || Boolean.TRUE.equals(ticketClaim);
			request.setAttribute(ATTR_TICKET_SALES_ALLOWED, ticketSalesAllowed);
			request.setAttribute(ATTR_ADMIN_PANEL_ACCESS, jwtService.readAdminPanelAccess(claims));
		} catch (InvalidTokenException e) {
			unauthorized(response, e.getMessage());
			return;
		}

		filterChain.doFilter(request, response);
	}

	private static boolean isPublic(String path, String method) {
		if (path.equals("/api/health") || path.equals("/api/info")) {
			return true;
		}
		if (path.equals("/api/printer/status") && "GET".equalsIgnoreCase(method)) {
			return true;
		}
		if (path.equals("/api/auth/login") && "POST".equalsIgnoreCase(method)) {
			return true;
		}
		return false;
	}

	private static void unauthorized(HttpServletResponse response, String message) throws IOException {
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write("{\"error\":\"" + escapeJson(message) + "\"}");
	}

	private static String escapeJson(String s) {
		return s.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}

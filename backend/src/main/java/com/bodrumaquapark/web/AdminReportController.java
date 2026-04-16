package com.bodrumaquapark.web;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.bodrumaquapark.entity.RoleCode;
import com.bodrumaquapark.security.JwtAuthenticationFilter;
import com.bodrumaquapark.service.AdminReportService;
import com.bodrumaquapark.web.dto.AdminDayCloseReportDto;
import com.bodrumaquapark.web.dto.AdminSummaryReportDto;
import com.bodrumaquapark.web.dto.ProductRevenueDto;
import com.bodrumaquapark.web.dto.SaleAreaRevenueDto;

@RestController
@RequestMapping("/api/admin/reports")
public class AdminReportController {

	private final AdminReportService adminReportService;

	public AdminReportController(AdminReportService adminReportService) {
		this.adminReportService = adminReportService;
	}

	/**
	 * Rapor servisinin ayakta olduğunu doğrulamak için (tarayıcı / curl). Ara sekmeler bu alt yolları çağırır.
	 */
	@GetMapping
	public Map<String, Object> index(@RequestAttribute(JwtAuthenticationFilter.ATTR_ROLE) RoleCode role) {
		requireAdmin(role);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("service", "admin-reports");
		body.put(
				"endpoints",
				List.of(
						"GET /api/admin/reports/summary?from=yyyy-MM-dd&to=yyyy-MM-dd",
						"GET /api/admin/reports/sales-by-sale-area?from=&to=",
						"GET /api/admin/reports/sales-by-product?from=&to=",
						"GET /api/admin/reports/day-close?date=&limit=500"));
		return body;
	}

	@GetMapping("/summary")
	public AdminSummaryReportDto summary(
			@RequestAttribute(JwtAuthenticationFilter.ATTR_ROLE) RoleCode role,
			@RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		requireAdmin(role);
		return adminReportService.summary(from, to);
	}

	@GetMapping("/sales-by-sale-area")
	public List<SaleAreaRevenueDto> salesBySaleArea(
			@RequestAttribute(JwtAuthenticationFilter.ATTR_ROLE) RoleCode role,
			@RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		requireAdmin(role);
		return adminReportService.salesBySaleArea(from, to);
	}

	@GetMapping("/sales-by-product")
	public List<ProductRevenueDto> salesByProduct(
			@RequestAttribute(JwtAuthenticationFilter.ATTR_ROLE) RoleCode role,
			@RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		requireAdmin(role);
		return adminReportService.salesByProduct(from, to);
	}

	@GetMapping("/day-close")
	public AdminDayCloseReportDto dayClose(
			@RequestAttribute(JwtAuthenticationFilter.ATTR_ROLE) RoleCode role,
			@RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam(name = "limit", required = false, defaultValue = "500") int limit) {
		requireAdmin(role);
		return adminReportService.dayClose(date, limit);
	}

	private static void requireAdmin(RoleCode role) {
		if (role != RoleCode.ADMIN) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu işlem için yönetici yetkisi gerekir");
		}
	}
}

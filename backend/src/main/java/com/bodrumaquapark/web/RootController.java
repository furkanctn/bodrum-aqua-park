package com.bodrumaquapark.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RootController {

	@Value("${spring.application.name:bodrum-aqua-park-api}")
	private String applicationName;

	@GetMapping("/api/info")
	public Map<String, Object> apiInfo() {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("name", applicationName);
		body.put("status", "UP");
		body.put("message", "API çalışıyor. Aşağıdaki uç noktaları deneyin.");
		Map<String, String> endpoints = new LinkedHashMap<>();
		endpoints.put("health", "/api/health");
		endpoints.put("authLogin", "/api/auth/login");
		endpoints.put("adminUsers", "/api/admin/users (JWT + ADMIN)");
		endpoints.put("adminProducts", "/api/admin/products (JWT + ADMIN)");
		endpoints.put("adminSaleAreas", "/api/admin/sale-areas (JWT + ADMIN)");
		endpoints.put("adminReports", "/api/admin/reports/* (JWT + ADMIN)");
		endpoints.put("saleAreas", "/api/sale-areas");
		endpoints.put("products", "/api/products");
		endpoints.put("h2Console", "/h2-console (sadece dev profili)");
		endpoints.put("printerStatus", "GET /api/printer/status (JWT gerekmez)");
		endpoints.put("printerPorts", "GET /api/printer/ports (JWT)");
		endpoints.put("printerTest", "POST /api/printer/test (JWT, body: port, baudRate, mode: full|nocut|minimal)");
		body.put("endpoints", endpoints);
		return body;
	}

	@GetMapping("/api/health")
	public Map<String, String> health() {
		return Map.of("status", "UP");
	}
}

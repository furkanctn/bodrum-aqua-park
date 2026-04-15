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
		body.put("endpoints", Map.of(
				"health", "/api/health",
				"authLogin", "/api/auth/login",
				"adminUsers", "/api/admin/users (JWT + ADMIN)",
				"adminProducts", "/api/admin/products (JWT + ADMIN)",
				"saleAreas", "/api/sale-areas",
				"products", "/api/products",
				"h2Console", "/h2-console (sadece dev profili)",
				"printerStatus", "GET /api/printer/status (JWT gerekmez)",
				"printerPorts", "GET /api/printer/ports (JWT)",
				"printerTest", "POST /api/printer/test (JWT, body: port, baudRate, mode: full|nocut|minimal)"));
		return body;
	}

	@GetMapping("/api/health")
	public Map<String, String> health() {
		return Map.of("status", "UP");
	}
}

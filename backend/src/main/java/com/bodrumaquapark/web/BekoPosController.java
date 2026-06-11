package com.bodrumaquapark.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bodrumaquapark.service.BekoPosService;
import com.bodrumaquapark.web.dto.BekoPosSendBasketRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/beko-pos")
@Validated
public class BekoPosController {

	private final BekoPosService bekoPosService;

	public BekoPosController(BekoPosService bekoPosService) {
		this.bekoPosService = bekoPosService;
	}

	@GetMapping("/status")
	public ResponseEntity<Map<String, Object>> status() {
		return ResponseEntity.ok(bekoPosService.statusForDisplay());
	}

	@PostMapping("/send-basket")
	public ResponseEntity<Map<String, Object>> sendBasket(@Valid @RequestBody BekoPosSendBasketRequest body) {
		Map<String, Object> result = bekoPosService.sendBasket(body);
		boolean ok = Boolean.TRUE.equals(result.get("ok"));
		if (!ok) {
			return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(result);
		}
		return ResponseEntity.ok(result);
	}

	@GetMapping("/settings")
	public ResponseEntity<Map<String, Object>> settings() {
		Map<String, Object> body = new LinkedHashMap<>(bekoPosService.statusForDisplay());
		body.remove("deviceInfoPreview");
		return ResponseEntity.ok(body);
	}
}

package com.bodrumaquapark.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bodrumaquapark.service.TurnstileScanResult;
import com.bodrumaquapark.service.TurnstileService;
import com.bodrumaquapark.web.dto.TurnstileScanRequest;
import com.bodrumaquapark.web.dto.TurnstileScanResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/turnstile")
public class TurnstileController {

	private final TurnstileService turnstileService;

	public TurnstileController(TurnstileService turnstileService) {
		this.turnstileService = turnstileService;
	}

	@PostMapping("/scan")
	public ResponseEntity<TurnstileScanResponse> scan(@Valid @RequestBody TurnstileScanRequest request) {
		TurnstileScanResult result = turnstileService.scan(request.cardUid());
		if (TurnstileScanResult.CODE_NOT_FOUND.equals(result.code())) {
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(TurnstileScanResponse.from(result));
		}
		return ResponseEntity.ok(TurnstileScanResponse.from(result));
	}
}

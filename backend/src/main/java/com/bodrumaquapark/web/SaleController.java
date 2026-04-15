package com.bodrumaquapark.web;

import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bodrumaquapark.security.JwtAuthenticationFilter;
import com.bodrumaquapark.service.SaleService;
import com.bodrumaquapark.web.dto.SaleRequest;
import com.bodrumaquapark.web.dto.SaleResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/sales")
public class SaleController {

	private final SaleService saleService;

	public SaleController(SaleService saleService) {
		this.saleService = saleService;
	}

	@PostMapping
	public ResponseEntity<SaleResponse> sell(@Valid @RequestBody SaleRequest request,
			@RequestAttribute(JwtAuthenticationFilter.ATTR_SALE_AREA_CODES) Set<String> allowedCodes) {
		return ResponseEntity
				.ok(SaleResponse.from(saleService.sell(request.cardUid(), request.productId(), allowedCodes)));
	}
}

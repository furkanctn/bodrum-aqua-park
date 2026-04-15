package com.bodrumaquapark.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.bodrumaquapark.repository.ProductRepository;
import com.bodrumaquapark.repository.SaleAreaRepository;
import com.bodrumaquapark.security.JwtAuthenticationFilter;
import com.bodrumaquapark.web.dto.ProductResponse;
import com.bodrumaquapark.web.dto.SaleAreaResponse;

@RestController
@RequestMapping("/api")
public class CatalogController {

	private final SaleAreaRepository saleAreaRepository;
	private final ProductRepository productRepository;

	public CatalogController(SaleAreaRepository saleAreaRepository, ProductRepository productRepository) {
		this.saleAreaRepository = saleAreaRepository;
		this.productRepository = productRepository;
	}

	@GetMapping("/sale-areas")
	public List<SaleAreaResponse> saleAreas(
			@RequestAttribute(JwtAuthenticationFilter.ATTR_SALE_AREA_CODES) Set<String> allowedCodes) {
		if (allowedCodes.isEmpty()) {
			return List.of();
		}
		return saleAreaRepository.findAllByCodeIn(new ArrayList<>(allowedCodes)).stream()
				.sorted((a, b) -> a.getCode().compareTo(b.getCode()))
				.map(SaleAreaResponse::from)
				.toList();
	}

	@GetMapping("/products")
	public List<ProductResponse> products(
			@RequestAttribute(JwtAuthenticationFilter.ATTR_SALE_AREA_CODES) Set<String> allowedCodes,
			@RequestParam(value = "saleAreaCode", required = false) String saleAreaCode) {
		if (allowedCodes.isEmpty()) {
			return List.of();
		}
		if (saleAreaCode != null && !saleAreaCode.isBlank()) {
			String code = saleAreaCode.trim();
			if (!allowedCodes.contains(code)) {
				throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu satış alanına erişim yetkiniz yok");
			}
			return productRepository.findByActiveTrueAndSaleArea_CodeOrderByNameAsc(code).stream()
					.map(ProductResponse::from)
					.toList();
		}
		return productRepository.findByActiveTrueAndSaleArea_CodeInOrderBySaleArea_CodeAscNameAsc(allowedCodes).stream()
				.map(ProductResponse::from)
				.toList();
	}
}

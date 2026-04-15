package com.bodrumaquapark.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.bodrumaquapark.entity.RoleCode;
import com.bodrumaquapark.security.JwtAuthenticationFilter;
import com.bodrumaquapark.service.AdminProductService;
import com.bodrumaquapark.web.dto.CreateProductRequest;
import com.bodrumaquapark.web.dto.ProductResponse;
import com.bodrumaquapark.web.dto.UpdateProductRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/products")
public class AdminProductController {

	private final AdminProductService adminProductService;

	public AdminProductController(AdminProductService adminProductService) {
		this.adminProductService = adminProductService;
	}

	@GetMapping
	public List<ProductResponse> list(@RequestAttribute(JwtAuthenticationFilter.ATTR_ROLE) RoleCode role) {
		requireAdmin(role);
		return adminProductService.listAll();
	}

	@PostMapping
	public ResponseEntity<ProductResponse> create(@Valid @RequestBody CreateProductRequest request,
			@RequestAttribute(JwtAuthenticationFilter.ATTR_ROLE) RoleCode role) {
		requireAdmin(role);
		ProductResponse created = adminProductService.create(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(created);
	}

	@PutMapping("/{id}")
	public ProductResponse update(@PathVariable("id") Long id, @Valid @RequestBody UpdateProductRequest request,
			@RequestAttribute(JwtAuthenticationFilter.ATTR_ROLE) RoleCode role) {
		requireAdmin(role);
		return adminProductService.update(id, request);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable("id") Long id,
			@RequestAttribute(JwtAuthenticationFilter.ATTR_ROLE) RoleCode role) {
		requireAdmin(role);
		adminProductService.softDelete(id);
		return ResponseEntity.noContent().build();
	}

	private static void requireAdmin(RoleCode role) {
		if (role != RoleCode.ADMIN) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu işlem için yönetici yetkisi gerekir");
		}
	}
}

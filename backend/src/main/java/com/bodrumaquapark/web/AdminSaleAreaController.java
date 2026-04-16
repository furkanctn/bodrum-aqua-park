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
import com.bodrumaquapark.security.AdminApiAccess;
import com.bodrumaquapark.security.JwtAuthenticationFilter;
import com.bodrumaquapark.service.AdminSaleAreaService;
import com.bodrumaquapark.web.dto.CreateSaleAreaRequest;
import com.bodrumaquapark.web.dto.SaleAreaAdminResponse;
import com.bodrumaquapark.web.dto.UpdateSaleAreaRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/sale-areas")
public class AdminSaleAreaController {

	private final AdminSaleAreaService adminSaleAreaService;

	public AdminSaleAreaController(AdminSaleAreaService adminSaleAreaService) {
		this.adminSaleAreaService = adminSaleAreaService;
	}

	@GetMapping
	public List<SaleAreaAdminResponse> list(@RequestAttribute(JwtAuthenticationFilter.ATTR_ROLE) RoleCode role,
			@RequestAttribute(JwtAuthenticationFilter.ATTR_ADMIN_PANEL_ACCESS) Boolean adminPanelAccess) {
		AdminApiAccess.require(role, adminPanelAccess);
		return adminSaleAreaService.listAll();
	}

	@PostMapping
	public ResponseEntity<SaleAreaAdminResponse> create(@Valid @RequestBody CreateSaleAreaRequest request,
			@RequestAttribute(JwtAuthenticationFilter.ATTR_ROLE) RoleCode role) {
		requireAdmin(role);
		SaleAreaAdminResponse created = adminSaleAreaService.create(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(created);
	}

	@PutMapping("/{id}")
	public SaleAreaAdminResponse update(@PathVariable("id") Long id, @Valid @RequestBody UpdateSaleAreaRequest request,
			@RequestAttribute(JwtAuthenticationFilter.ATTR_ROLE) RoleCode role) {
		requireAdmin(role);
		return adminSaleAreaService.update(id, request);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable("id") Long id,
			@RequestAttribute(JwtAuthenticationFilter.ATTR_ROLE) RoleCode role) {
		requireAdmin(role);
		adminSaleAreaService.delete(id);
		return ResponseEntity.noContent().build();
	}

	private static void requireAdmin(RoleCode role) {
		if (role != RoleCode.ADMIN) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu işlem için yönetici yetkisi gerekir");
		}
	}
}

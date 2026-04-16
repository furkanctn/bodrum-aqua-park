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

import com.bodrumaquapark.entity.RoleCode;
import com.bodrumaquapark.security.AdminApiAccess;
import com.bodrumaquapark.security.JwtAuthenticationFilter;
import com.bodrumaquapark.service.AdminMenuPageService;
import com.bodrumaquapark.web.dto.CreateMenuPageRequest;
import com.bodrumaquapark.web.dto.MenuPageAdminResponse;
import com.bodrumaquapark.web.dto.UpdateMenuPageRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/menu-pages")
public class AdminMenuPageController {

	private final AdminMenuPageService adminMenuPageService;

	public AdminMenuPageController(AdminMenuPageService adminMenuPageService) {
		this.adminMenuPageService = adminMenuPageService;
	}

	@GetMapping
	public List<MenuPageAdminResponse> list(@RequestAttribute(JwtAuthenticationFilter.ATTR_ROLE) RoleCode role,
			@RequestAttribute(JwtAuthenticationFilter.ATTR_ADMIN_PANEL_ACCESS) Boolean adminPanelAccess) {
		AdminApiAccess.require(role, adminPanelAccess);
		return adminMenuPageService.listAll();
	}

	@PostMapping
	public ResponseEntity<MenuPageAdminResponse> create(@Valid @RequestBody CreateMenuPageRequest request,
			@RequestAttribute(JwtAuthenticationFilter.ATTR_ROLE) RoleCode role,
			@RequestAttribute(JwtAuthenticationFilter.ATTR_ADMIN_PANEL_ACCESS) Boolean adminPanelAccess) {
		AdminApiAccess.require(role, adminPanelAccess);
		MenuPageAdminResponse created = adminMenuPageService.create(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(created);
	}

	@PutMapping("/{id}")
	public MenuPageAdminResponse update(@PathVariable("id") Long id, @Valid @RequestBody UpdateMenuPageRequest request,
			@RequestAttribute(JwtAuthenticationFilter.ATTR_ROLE) RoleCode role,
			@RequestAttribute(JwtAuthenticationFilter.ATTR_ADMIN_PANEL_ACCESS) Boolean adminPanelAccess) {
		AdminApiAccess.require(role, adminPanelAccess);
		return adminMenuPageService.update(id, request);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable("id") Long id,
			@RequestAttribute(JwtAuthenticationFilter.ATTR_ROLE) RoleCode role,
			@RequestAttribute(JwtAuthenticationFilter.ATTR_ADMIN_PANEL_ACCESS) Boolean adminPanelAccess) {
		AdminApiAccess.require(role, adminPanelAccess);
		adminMenuPageService.delete(id);
		return ResponseEntity.noContent().build();
	}
}

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
import com.bodrumaquapark.service.AdminTicketAgeGroupService;
import com.bodrumaquapark.web.dto.TicketAgeGroupCreateRequest;
import com.bodrumaquapark.web.dto.TicketAgeGroupResponse;
import com.bodrumaquapark.web.dto.TicketAgeGroupUpdateRequest;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/ticket-age-groups")
public class AdminTicketAgeGroupController {

	private final AdminTicketAgeGroupService service;

	public AdminTicketAgeGroupController(AdminTicketAgeGroupService service) {
		this.service = service;
	}

	@GetMapping
	public List<TicketAgeGroupResponse> list(@RequestAttribute(JwtAuthenticationFilter.ATTR_ROLE) RoleCode role,
			@RequestAttribute(JwtAuthenticationFilter.ATTR_ADMIN_PANEL_ACCESS) Boolean adminPanelAccess) {
		AdminApiAccess.require(role, adminPanelAccess);
		return service.listAll();
	}

	@PostMapping
	public ResponseEntity<TicketAgeGroupResponse> create(@Valid @RequestBody TicketAgeGroupCreateRequest request,
			@RequestAttribute(JwtAuthenticationFilter.ATTR_ROLE) RoleCode role,
			@RequestAttribute(JwtAuthenticationFilter.ATTR_ADMIN_PANEL_ACCESS) Boolean adminPanelAccess) {
		AdminApiAccess.require(role, adminPanelAccess);
		TicketAgeGroupResponse created = service.create(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(created);
	}

	@PutMapping("/{id}")
	public TicketAgeGroupResponse update(@PathVariable("id") long id, @Valid @RequestBody TicketAgeGroupUpdateRequest request,
			@RequestAttribute(JwtAuthenticationFilter.ATTR_ROLE) RoleCode role,
			@RequestAttribute(JwtAuthenticationFilter.ATTR_ADMIN_PANEL_ACCESS) Boolean adminPanelAccess) {
		AdminApiAccess.require(role, adminPanelAccess);
		return service.update(id, request);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable("id") long id,
			@RequestAttribute(JwtAuthenticationFilter.ATTR_ROLE) RoleCode role,
			@RequestAttribute(JwtAuthenticationFilter.ATTR_ADMIN_PANEL_ACCESS) Boolean adminPanelAccess) {
		AdminApiAccess.require(role, adminPanelAccess);
		service.delete(id);
		return ResponseEntity.noContent().build();
	}
}

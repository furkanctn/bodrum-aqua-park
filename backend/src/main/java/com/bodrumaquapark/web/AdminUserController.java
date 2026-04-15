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
import com.bodrumaquapark.service.StaffUserService;
import com.bodrumaquapark.web.dto.CreateUserRequest;
import com.bodrumaquapark.web.dto.UpdateUserRequest;
import com.bodrumaquapark.web.dto.UserResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

	private final StaffUserService staffUserService;

	public AdminUserController(StaffUserService staffUserService) {
		this.staffUserService = staffUserService;
	}

	@GetMapping
	public List<UserResponse> list(@RequestAttribute(JwtAuthenticationFilter.ATTR_ROLE) RoleCode role) {
		requireAdmin(role);
		return staffUserService.listAll();
	}

	@GetMapping("/{id}")
	public UserResponse get(
			@PathVariable("id") Long id,
			@RequestAttribute(JwtAuthenticationFilter.ATTR_ROLE) RoleCode role) {
		requireAdmin(role);
		return staffUserService.get(id);
	}

	@PostMapping
	public ResponseEntity<UserResponse> create(
			@Valid @RequestBody CreateUserRequest request,
			@RequestAttribute(JwtAuthenticationFilter.ATTR_ROLE) RoleCode role) {
		requireAdmin(role);
		UserResponse created = staffUserService.create(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(created);
	}

	@PutMapping("/{id}")
	public UserResponse update(
			@PathVariable Long id,
			@Valid @RequestBody UpdateUserRequest request,
			@RequestAttribute(JwtAuthenticationFilter.ATTR_ROLE) RoleCode role,
			@RequestAttribute(JwtAuthenticationFilter.ATTR_USER_ID) String currentUserId) {
		requireAdmin(role);
		return staffUserService.update(id, request, currentUserId);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(
			@PathVariable("id") Long id,
			@RequestAttribute(JwtAuthenticationFilter.ATTR_ROLE) RoleCode role,
			@RequestAttribute(JwtAuthenticationFilter.ATTR_USER_ID) String currentUserId) {
		requireAdmin(role);
		staffUserService.delete(id, currentUserId);
		return ResponseEntity.noContent().build();
	}

	private static void requireAdmin(RoleCode role) {
		if (role != RoleCode.ADMIN) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu işlem için yönetici yetkisi gerekir");
		}
	}
}

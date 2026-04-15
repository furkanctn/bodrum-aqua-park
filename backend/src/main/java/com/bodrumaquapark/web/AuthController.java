package com.bodrumaquapark.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.bodrumaquapark.security.JwtAuthenticationFilter;
import com.bodrumaquapark.service.AuthService;
import com.bodrumaquapark.service.AuthService.LoginResult;
import com.bodrumaquapark.service.AuthService.PosPermissions;
import com.bodrumaquapark.service.StaffUserService;
import com.bodrumaquapark.web.dto.LoginRequest;
import com.bodrumaquapark.web.dto.UserResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;
	private final StaffUserService staffUserService;

	public AuthController(AuthService authService, StaffUserService staffUserService) {
		this.authService = authService;
		this.staffUserService = staffUserService;
	}

	private void putPosPermissions(Map<String, Object> body, PosPermissions p) {
		body.put("ticketSalesAllowed", p.ticketSalesAllowed());
		body.put("balanceLoadAllowed", p.balanceLoadAllowed());
	}

	@PostMapping("/login")
	public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
		LoginResult r = authService.login(request.userId(), request.password());
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("accessToken", r.accessToken());
		body.put("tokenType", "Bearer");
		body.put("userId", r.userId());
		body.put("role", r.role().name());
		body.put("displayName", r.displayName() != null ? r.displayName() : r.userId());
		body.put("saleAreaCodes", r.saleAreaCodes());
		putPosPermissions(body, new PosPermissions(r.ticketSalesAllowed(), r.balanceLoadAllowed()));
		return ResponseEntity.ok(body);
	}

	/** Oturum bilgisi (JWT gerekli) */
	@GetMapping("/me")
	public ResponseEntity<Map<String, Object>> me(
			@RequestAttribute(JwtAuthenticationFilter.ATTR_USER_ID) String userId) {
		UserResponse u = staffUserService.findByUserId(userId)
				.orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
						org.springframework.http.HttpStatus.NOT_FOUND, "Kullanıcı bulunamadı"));
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("userId", u.userId());
		body.put("role", u.role().name());
		body.put("displayName", u.displayName() != null ? u.displayName() : u.userId());
		body.put("active", u.active());
		body.put("saleAreaCodes", u.saleAreaCodes());
		putPosPermissions(body, authService.effectivePosPermissions(userId));
		return ResponseEntity.ok(body);
	}
}

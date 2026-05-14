package com.bodrumaquapark.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.bodrumaquapark.entity.RoleCode;
import com.bodrumaquapark.security.JwtAuthenticationFilter;
import com.bodrumaquapark.service.TurnstileAdminService;
import com.bodrumaquapark.web.dto.TurnstileDeviceStatusResponse;

@RestController
@RequestMapping("/api/admin/turnstiles")
public class AdminTurnstileController {

	private final TurnstileAdminService turnstileAdminService;

	public AdminTurnstileController(TurnstileAdminService turnstileAdminService) {
		this.turnstileAdminService = turnstileAdminService;
	}

	@GetMapping
	public List<TurnstileDeviceStatusResponse> list(@RequestAttribute(JwtAuthenticationFilter.ATTR_ROLE) RoleCode role) {
		requireAdmin(role);
		return turnstileAdminService.listDeviceStatuses();
	}

	private static void requireAdmin(RoleCode role) {
		if (role != RoleCode.ADMIN) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu işlem için yönetici yetkisi gerekir");
		}
	}
}

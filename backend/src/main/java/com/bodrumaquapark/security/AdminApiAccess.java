package com.bodrumaquapark.security;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.bodrumaquapark.entity.RoleCode;

/**
 * /api/admin/* uçları: tam yönetici veya yalnızca yönetim paneli yetkisi verilmiş kasa personeli.
 */
public final class AdminApiAccess {

	private AdminApiAccess() {
	}

	public static void require(RoleCode role, Boolean adminPanelAccess) {
		if (role == RoleCode.ADMIN) {
			return;
		}
		if (Boolean.TRUE.equals(adminPanelAccess)) {
			return;
		}
		throw new ResponseStatusException(HttpStatus.FORBIDDEN,
				"Bu işlem için yönetici veya yönetim paneli erişim yetkisi gerekir");
	}
}

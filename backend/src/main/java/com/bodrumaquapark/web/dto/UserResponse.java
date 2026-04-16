package com.bodrumaquapark.web.dto;

import java.util.List;

import com.bodrumaquapark.entity.RoleCode;
import com.bodrumaquapark.entity.SaleArea;
import com.bodrumaquapark.entity.StaffUser;

public record UserResponse(
		Long id,
		String userId,
		String displayName,
		boolean active,
		RoleCode role,
		List<String> saleAreaCodes,
		boolean ticketSalesAllowed,
		boolean balanceLoadAllowed,
		/** Etkin: yönetici veya bayraklı kullanıcı — /api/auth/me ile uyumlu */
		boolean adminPanelAccess,
		/** Veritabanı bayrağı (düzenleme formu; yöneticide DB’de false olsa bile erişim tamdır) */
		boolean adminPanelGranted
) {
	public static UserResponse from(StaffUser u) {
		List<String> codes = u.getSaleAreas().stream().map(SaleArea::getCode).sorted().toList();
		boolean effective = u.getRole() == RoleCode.ADMIN || u.isAdminPanelAccess();
		return new UserResponse(u.getId(), u.getUserId(), u.getDisplayName(), u.isActive(), u.getRole(), codes,
				u.isTicketSalesAllowed(), u.isBalanceLoadAllowed(), effective, u.isAdminPanelAccess());
	}
}

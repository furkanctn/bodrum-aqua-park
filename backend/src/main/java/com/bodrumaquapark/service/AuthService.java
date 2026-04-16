package com.bodrumaquapark.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.bodrumaquapark.entity.RoleCode;
import com.bodrumaquapark.entity.SaleArea;
import com.bodrumaquapark.entity.StaffUser;
import com.bodrumaquapark.repository.SaleAreaRepository;
import com.bodrumaquapark.repository.StaffUserRepository;
import com.bodrumaquapark.security.JwtService;

@Service
public class AuthService {

	private final StaffUserRepository staffUserRepository;
	private final SaleAreaRepository saleAreaRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	public AuthService(StaffUserRepository staffUserRepository, SaleAreaRepository saleAreaRepository,
			PasswordEncoder passwordEncoder, JwtService jwtService) {
		this.staffUserRepository = staffUserRepository;
		this.saleAreaRepository = saleAreaRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
	}

	public LoginResult login(String userId, String password) {
		StaffUser u = staffUserRepository.loadWithSaleAreasByUserId(userId.trim())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Geçersiz kullanıcı veya şifre"));
		if (!u.isActive()) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Hesap devre dışı");
		}
		if (!passwordEncoder.matches(password, u.getPasswordHash())) {
			throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Geçersiz kullanıcı veya şifre");
		}
		List<String> areaCodes = resolveSaleAreaCodesForToken(u);
		boolean ticket = effectiveTicketSales(u);
		boolean balance = effectiveBalanceLoad(u);
		boolean adminPanel = effectiveAdminPanelAccess(u);
		String token = jwtService.createToken(u.getUserId(), u.getRole(), areaCodes, ticket, balance, adminPanel);
		return new LoginResult(token, u.getUserId(), u.getRole(), u.getDisplayName(), areaCodes, ticket, balance,
				adminPanel);
	}

	private boolean effectiveAdminPanelAccess(StaffUser u) {
		if (u.getRole() == RoleCode.ADMIN) {
			return true;
		}
		return u.isAdminPanelAccess();
	}

	private boolean effectiveTicketSales(StaffUser u) {
		if (u.getRole() == RoleCode.ADMIN || u.getRole() == RoleCode.TICKET) {
			return true;
		}
		return u.isTicketSalesAllowed();
	}

	private boolean effectiveBalanceLoad(StaffUser u) {
		if (u.getRole() == RoleCode.ADMIN) {
			return true;
		}
		return u.isBalanceLoadAllowed();
	}

	/** /api/auth/me ve POS için etkin yetkiler (yönetici her zaman tam) */
	public PosPermissions effectivePosPermissions(String userId) {
		StaffUser u = staffUserRepository.loadWithSaleAreasByUserId(userId.trim())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kullanıcı bulunamadı"));
		return new PosPermissions(effectiveTicketSales(u), effectiveBalanceLoad(u));
	}

	public record PosPermissions(boolean ticketSalesAllowed, boolean balanceLoadAllowed) {
	}

	/**
	 * Yönetici ve atanmış alan yoksa: tüm satış alanları. Diğer roller: yalnızca atanan kodlar.
	 */
	private List<String> resolveSaleAreaCodesForToken(StaffUser u) {
		if (u.getRole() == RoleCode.ADMIN && u.getSaleAreas().isEmpty()) {
			return saleAreaRepository.findAll().stream().map(SaleArea::getCode).sorted().toList();
		}
		return u.getSaleAreas().stream().map(SaleArea::getCode).sorted().toList();
	}

	public record LoginResult(String accessToken, String userId, RoleCode role, String displayName,
			List<String> saleAreaCodes, boolean ticketSalesAllowed, boolean balanceLoadAllowed,
			boolean adminPanelAccess) {
	}
}

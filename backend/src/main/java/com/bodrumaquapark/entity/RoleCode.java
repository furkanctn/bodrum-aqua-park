package com.bodrumaquapark.entity;

/**
 * Kasa personeli rolleri. ADMIN: yönetim paneli (satış alanı atanması zorunlu değil).
 * TICKET: yalnızca kart satış ekranında bilet / yaş grubu.
 */
public enum RoleCode {
	ADMIN,
	SUPERVISOR,
	CASHIER,
	TICKET
}

package com.bodrumaquapark.entity;

/**
 * Kasa personeli rolleri. ADMIN: kullanıcı/rol yönetimi + tüm POS işlemleri.
 * TICKET: yalnızca kart satış ekranında bilet / yaş grubu (ürün alanı atanması zorunlu değil).
 */
public enum RoleCode {
	ADMIN,
	SUPERVISOR,
	CASHIER,
	TICKET
}

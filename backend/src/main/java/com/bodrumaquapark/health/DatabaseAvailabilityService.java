package com.bodrumaquapark.health;

import java.sql.Connection;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Veritabanına hafif {@code Connection.isValid()} pingi atar ve sonucu kısa süreli (TTL) bellek içinde önbellekler.
 * Her HTTP isteğinde DB'yi bombalamadan, gerçek bağlantı kopuğunda hızlı 503 dönmemizi sağlar.
 */
@Component
public class DatabaseAvailabilityService {

	private static final Logger log = LoggerFactory.getLogger(DatabaseAvailabilityService.class);

	private final DataSource dataSource;
	private final long checkTtlMs;
	private final AtomicLong lastCheckMillis = new AtomicLong(0L);
	private volatile boolean lastResult = false;
	private volatile boolean previousLoggedState = true; // başlangıçta "açıkmış gibi"

	public DatabaseAvailabilityService(DataSource dataSource,
			@Value("${app.startup.db-guard.check-ttl-ms:5000}") long checkTtlMs) {
		this.dataSource = dataSource;
		this.checkTtlMs = Math.max(500L, checkTtlMs);
	}

	/** Önbellekli sonuç; süre dolduysa yeniden ping atar. */
	public boolean isAvailable() {
		long now = System.currentTimeMillis();
		long last = lastCheckMillis.get();
		if (last != 0L && now - last < checkTtlMs) {
			return lastResult;
		}
		boolean ok = ping();
		lastResult = ok;
		lastCheckMillis.set(now);
		if (ok != previousLoggedState) {
			if (ok) {
				log.warn("Veritabanı tekrar erişilebilir; API istekleri kabul edilmeye devam ediyor.");
			} else {
				log.warn("Veritabanı erişilemiyor; /api/** isteklerine 503 dönülecek.");
			}
			previousLoggedState = ok;
		}
		return ok;
	}

	private boolean ping() {
		try (Connection c = dataSource.getConnection()) {
			return c.isValid(2);
		} catch (Exception e) {
			log.debug("DB ping hatası: {}", e.getMessage());
			return false;
		}
	}
}

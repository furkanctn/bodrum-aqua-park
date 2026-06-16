package com.bodrumaquapark.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.hibernate.Session;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.Locale;

@Service
public class TicketSchemaMigrationService {

	private static final Logger log = LoggerFactory.getLogger(TicketSchemaMigrationService.class);

	@PersistenceContext
	private EntityManager entityManager;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void migrateAgencyTicketSchemaIfNeeded() {
		addBooleanColumnIfMissing("ticket_age_groups", "agency_complimentary", false);
		addLongColumnIfMissing("card_ledger", "ticket_age_group_id");
		addIntegerColumnIfMissing("card_ledger", "line_quantity");
		ensureCardLedgerTypeAllowsDailyReset();
	}

	private void ensureCardLedgerTypeAllowsDailyReset() {
		if (!isH2()) {
			return;
		}
		try {
			entityManager.createNativeQuery(
					"ALTER TABLE card_ledger ALTER COLUMN type ENUM("
							+ "'ENTRY','SALE','LOAD_CASH','LOAD_CARD','LOAD_AGENCY',"
							+ "'TICKET_CASH','TICKET_CARD','TICKET_CREDIT','REFUND_CASH','DAILY_RESET'"
							+ ")")
					.executeUpdate();
		} catch (Exception ex) {
			String msg = ex.getMessage() != null ? ex.getMessage().toLowerCase(Locale.ROOT) : "";
			if (msg.contains("daily_reset")) {
				log.warn("card_ledger.type enum güncellenemedi (DAILY_RESET): {}", ex.getMessage());
			}
		}
	}

	private void addBooleanColumnIfMissing(String table, String column, boolean defaultValue) {
		if (columnExists(table, column)) {
			return;
		}
		log.info("Şema güncelleniyor: {}.{} (BOOLEAN)", table, column);
		entityManager.createNativeQuery(
				"ALTER TABLE " + table + " ADD COLUMN " + column + " BOOLEAN NOT NULL DEFAULT "
						+ (defaultValue ? "TRUE" : "FALSE"))
				.executeUpdate();
	}

	private void addLongColumnIfMissing(String table, String column) {
		if (columnExists(table, column)) {
			return;
		}
		log.info("Şema güncelleniyor: {}.{} (BIGINT)", table, column);
		entityManager.createNativeQuery("ALTER TABLE " + table + " ADD COLUMN " + column + " BIGINT").executeUpdate();
	}

	private void addIntegerColumnIfMissing(String table, String column) {
		if (columnExists(table, column)) {
			return;
		}
		log.info("Şema güncelleniyor: {}.{} (INTEGER)", table, column);
		entityManager.createNativeQuery("ALTER TABLE " + table + " ADD COLUMN " + column + " INTEGER")
				.executeUpdate();
	}

	private boolean columnExists(String table, String column) {
		try {
			Session session = entityManager.unwrap(Session.class);
			return Boolean.TRUE.equals(session.doReturningWork(connection -> {
				DatabaseMetaData meta = connection.getMetaData();
				String tablePattern = meta.storesUpperCaseIdentifiers() ? table.toUpperCase() : table;
				String columnPattern = meta.storesUpperCaseIdentifiers() ? column.toUpperCase() : column;
				try (ResultSet cols = meta.getColumns(null, null, tablePattern, columnPattern)) {
					return cols.next();
				}
			}));
		} catch (Exception ex) {
			return false;
		}
	}

	private boolean isH2() {
		try {
			Session session = entityManager.unwrap(Session.class);
			return Boolean.TRUE.equals(session.doReturningWork(connection -> {
				DatabaseMetaData meta = connection.getMetaData();
				String name = meta.getDatabaseProductName();
				return name != null && name.toLowerCase(Locale.ROOT).contains("h2");
			}));
		} catch (Exception ex) {
			return false;
		}
	}
}

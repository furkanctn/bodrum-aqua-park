package com.bodrumaquapark.config;

import java.sql.SQLException;
import java.sql.SQLTransientException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.CannotCreateTransactionException;

/**
 * Açılış {@link ApplicationRunner} adımlarını veritabanı kapalıyken yumuşak başarısız hale getirir:
 * bağlantı/JDBC hatasında uygulama açılışını engellemez, uyarı logu atar ve diğer adımlara devam eder.
 *
 * <p>Diğer (DB ile ilgisiz) istisnalar yine yukarı taşınır.
 */
public final class BootstrapResilience {

	private static final Logger log = LoggerFactory.getLogger(BootstrapResilience.class);

	/**
	 * İlk bootstrap adımı DB hatasıyla karşılaşınca true olur; sonraki adımlar hızla atlanır
	 * (tekrar 5 sn Hikari timeout'a takılmadan). Uygulamanın açılış süresini DB kapalıyken
	 * onlarca saniyeden bir kaç saniyeye indirir. POS yeniden başlatılınca tekrar sıfırlanır.
	 */
	private static volatile boolean dbKnownDown = false;

	private BootstrapResilience() {
	}

	/** Bir bootstrap adımını DB bağlantı hatalarına karşı dirençli şekilde sarmalar. */
	public static ApplicationRunner safely(String stepName, ApplicationRunner delegate) {
		return (ApplicationArguments args) -> safelyRun(stepName, () -> delegate.run(args));
	}

	/** Doğrudan kod bloğu için aynı koruma. */
	public static void safelyRun(String stepName, ThrowingRunnable runnable) {
		if (dbKnownDown) {
			log.debug("Bootstrap atlandı (DB önceki adımda erişilemez tespit edildi) — {}", stepName);
			return;
		}
		try {
			runnable.run();
		} catch (CannotCreateTransactionException | DataAccessResourceFailureException ex) {
			markDownAndWarn(stepName, ex);
		} catch (DataAccessException ex) {
			if (isDbConnectivityError(ex)) {
				markDownAndWarn(stepName, ex);
			} else {
				log.error("Bootstrap hata — {}: {}", stepName, ex.getMessage(), ex);
			}
		} catch (RuntimeException ex) {
			if (isDbConnectivityError(ex)) {
				markDownAndWarn(stepName, ex);
			} else {
				throw ex;
			}
		} catch (Exception ex) {
			if (isDbConnectivityError(ex)) {
				markDownAndWarn(stepName, ex);
			} else {
				throw new IllegalStateException("Bootstrap adımı başarısız: " + stepName, ex);
			}
		}
	}

	private static void markDownAndWarn(String stepName, Throwable ex) {
		if (!dbKnownDown) {
			dbKnownDown = true;
			log.warn(
					"Veritabanı erişilemiyor; kalan bootstrap adımları atlanacak. İlk hata — {}: {}",
					stepName, rootMessage(ex));
		} else {
			log.debug("Bootstrap atlandı — {}: {}", stepName, rootMessage(ex));
		}
	}

	@FunctionalInterface
	public interface ThrowingRunnable {
		void run() throws Exception;
	}

	private static boolean isDbConnectivityError(Throwable ex) {
		Throwable c = ex;
		for (int i = 0; c != null && i < 10; i++) {
			if (c instanceof SQLTransientException) {
				return true;
			}
			if (c instanceof SQLException sql) {
				String state = sql.getSQLState();
				if (state != null && (state.startsWith("08") || state.startsWith("57"))) {
					return true;
				}
			}
			String n = c.getClass().getName();
			if (n.contains("ConnectException")
					|| n.contains("SQLNonTransientConnectionException")
					|| n.contains("HikariPool")
					|| n.contains("JDBCConnectionException")
					|| n.contains("PSQLException")) {
				return true;
			}
			if (c.getCause() == c) {
				break;
			}
			c = c.getCause();
		}
		return false;
	}

	private static String rootMessage(Throwable ex) {
		Throwable c = ex;
		while (c.getCause() != null && c.getCause() != c) {
			c = c.getCause();
		}
		String msg = c.getMessage();
		return msg != null ? msg : c.getClass().getSimpleName();
	}
}

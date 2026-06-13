package com.bodrumaquapark.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.bodrumaquapark.entity.MenuPage;
import com.bodrumaquapark.entity.Product;
import com.bodrumaquapark.entity.SaleArea;
import com.bodrumaquapark.repository.MenuPageRepository;
import com.bodrumaquapark.repository.ProductRepository;
import com.bodrumaquapark.repository.SaleAreaRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.hibernate.Session;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;

@Service
public class CatalogMigrationService {

	private static final Logger log = LoggerFactory.getLogger(CatalogMigrationService.class);

	@PersistenceContext
	private EntityManager entityManager;

	private final MenuPageRepository menuPageRepository;
	private final SaleAreaRepository saleAreaRepository;
	private final ProductRepository productRepository;

	public CatalogMigrationService(MenuPageRepository menuPageRepository, SaleAreaRepository saleAreaRepository,
			ProductRepository productRepository) {
		this.menuPageRepository = menuPageRepository;
		this.saleAreaRepository = saleAreaRepository;
		this.productRepository = productRepository;
	}

	/**
	 * POS/PostgreSQL profilinde ddl-auto=none olduğu için join tablosu otomatik oluşmaz; önce garanti edilir.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void ensureCatalogJoinTableExists() {
		if (tableExists("sale_area_menu_pages")) {
			return;
		}
		log.info("Şema güncelleniyor: sale_area_menu_pages tablosu oluşturuluyor…");
		entityManager.createNativeQuery("""
				CREATE TABLE sale_area_menu_pages (
					sale_area_id BIGINT NOT NULL,
					menu_page_id BIGINT NOT NULL,
					PRIMARY KEY (sale_area_id, menu_page_id),
					CONSTRAINT fk_samp_sale_area FOREIGN KEY (sale_area_id) REFERENCES sale_areas(id),
					CONSTRAINT fk_samp_menu_page FOREIGN KEY (menu_page_id) REFERENCES menu_pages(id)
				)
				""").executeUpdate();
		log.info("sale_area_menu_pages tablosu oluşturuldu.");
	}

	/**
	 * Eski şemada menü sayfası satış alanına bağlıydı; bağımsız menü + çoktan-çoğa ilişkiye taşır.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void migrateLegacyMenuAreaLinksIfNeeded() {
		ensureCatalogJoinTableExists();
		if (!columnExists("menu_pages", "sale_area_id")) {
			dropLegacyColumnIfPresent("products", "sale_area_id");
			return;
		}
		log.info("Eski menü–satış alanı bağlantıları taşınıyor…");
		List<Object[]> rows = legacyMenuAreaRows();
		if (!rows.isEmpty()) {
			deduplicateMenuCodes(rows);
			rows = legacyMenuAreaRows();
			for (Object[] row : rows) {
				long menuId = ((Number) row[0]).longValue();
				long saleAreaId = ((Number) row[1]).longValue();
				linkMenuToSaleArea(menuId, saleAreaId);
			}
			log.info("Menü–satış alanı taşıması tamamlandı ({} satır).", rows.size());
		}
		relaxLegacyNotNullIfPresent("menu_pages", "sale_area_id");
		relaxLegacyNotNullIfPresent("products", "sale_area_id");
		dropLegacyColumnIfPresent("menu_pages", "sale_area_id");
		dropLegacyColumnIfPresent("products", "sale_area_id");
	}

	/** Eski sütun kaldırılamasa bile yeni menü eklemeyi engelleyen NOT NULL kısıtını kaldırır. */
	private void relaxLegacyNotNullIfPresent(String table, String column) {
		if (!columnExists(table, column)) {
			return;
		}
		try {
			entityManager.createNativeQuery(
					"ALTER TABLE " + table + " ALTER COLUMN " + column + " DROP NOT NULL").executeUpdate();
			log.info("{}.{} NOT NULL kısıtı kaldırıldı.", table, column);
		} catch (Exception ex) {
			log.debug("{}.{} NOT NULL kaldırılamadı (zaten nullable olabilir): {}", table, column, ex.getMessage());
		}
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void deduplicateMenuPageCodesIfNeeded() {
		@SuppressWarnings("unchecked")
		List<String> duplicateCodes = entityManager.createNativeQuery("""
				SELECT code FROM menu_pages GROUP BY code HAVING COUNT(*) > 1
				""").getResultList();
		if (duplicateCodes.isEmpty()) {
			return;
		}
		log.info("Yinelenen menü kodları birleştiriliyor ({} kod)…", duplicateCodes.size());
		for (String code : duplicateCodes) {
			mergeDuplicateMenuPages(code);
		}
		log.info("Menü kodu birleştirme tamamlandı.");
	}

	private void mergeDuplicateMenuPages(String code) {
		@SuppressWarnings("unchecked")
		List<Long> ids = entityManager.createNativeQuery(
				"SELECT id FROM menu_pages WHERE code = :code ORDER BY id ASC")
				.setParameter("code", code)
				.getResultList();
		if (ids.size() < 2) {
			return;
		}
		long keeperId = ids.get(0);
		for (int i = 1; i < ids.size(); i++) {
			long dupId = ids.get(i);
			entityManager.createNativeQuery("UPDATE products SET menu_page_id = :keeper WHERE menu_page_id = :dup")
					.setParameter("keeper", keeperId)
					.setParameter("dup", dupId)
					.executeUpdate();
			if (tableExists("sale_area_menu_pages")) {
				entityManager.createNativeQuery("""
						INSERT INTO sale_area_menu_pages (sale_area_id, menu_page_id)
						SELECT sale_area_id, :keeper FROM sale_area_menu_pages WHERE menu_page_id = :dup
						ON CONFLICT DO NOTHING
						""")
						.setParameter("keeper", keeperId)
						.setParameter("dup", dupId)
						.executeUpdate();
				entityManager.createNativeQuery("DELETE FROM sale_area_menu_pages WHERE menu_page_id = :dup")
						.setParameter("dup", dupId)
						.executeUpdate();
			}
			entityManager.createNativeQuery("DELETE FROM menu_pages WHERE id = :dup")
					.setParameter("dup", dupId)
					.executeUpdate();
			log.info("Menü birleştirildi: code={} dupId={} → keeperId={}", code, dupId, keeperId);
		}
	}

	@Transactional
	public void ensureOrphanProductsHaveMenu() {
		MenuPage genel = menuPageRepository.findFirstByCodeOrderByIdAsc("GENEL").orElseGet(() -> menuPageRepository
				.save(new MenuPage("GENEL", "Genel", 0)));
		for (Product p : productRepository.findByMenuPageIsNull()) {
			p.setMenuPage(genel);
			productRepository.save(p);
		}
	}

	private boolean tableExists(String table) {
		try {
			Session session = entityManager.unwrap(Session.class);
			return Boolean.TRUE.equals(session.doReturningWork(connection -> {
				DatabaseMetaData meta = connection.getMetaData();
				String tablePattern = meta.storesUpperCaseIdentifiers() ? table.toUpperCase() : table;
				try (ResultSet tables = meta.getTables(null, null, tablePattern, new String[] { "TABLE" })) {
					return tables.next();
				}
			}));
		} catch (Exception ex) {
			return false;
		}
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

	@SuppressWarnings("unchecked")
	private List<Object[]> legacyMenuAreaRows() {
		return entityManager
				.createNativeQuery("SELECT id, sale_area_id, code FROM menu_pages WHERE sale_area_id IS NOT NULL")
				.getResultList();
	}

	private void dropLegacyColumnIfPresent(String table, String column) {
		if (!columnExists(table, column)) {
			return;
		}
		log.info("Eski {}.{} sütunu kaldırılıyor…", table, column);
		dropForeignKeysOnColumn(table, column);
		try {
			entityManager.createNativeQuery("ALTER TABLE " + table + " DROP COLUMN " + column).executeUpdate();
			log.info("{}.{} kaldırıldı.", table, column);
		} catch (Exception ex) {
			log.warn("DROP COLUMN {}.{} başarısız, IF EXISTS deneniyor: {}", table, column, ex.getMessage());
			try {
				entityManager.createNativeQuery("ALTER TABLE " + table + " DROP COLUMN IF EXISTS " + column)
						.executeUpdate();
				log.info("{}.{} kaldırıldı (IF EXISTS).", table, column);
			} catch (Exception ex2) {
				log.error("Eski sütun kaldırılamadı: {}.{}", table, column, ex2);
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void dropForeignKeysOnColumn(String table, String column) {
		List<String> names = entityManager.createNativeQuery("""
				SELECT DISTINCT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
				WHERE UPPER(TABLE_NAME) = :table AND UPPER(COLUMN_NAME) = :column
				""")
				.setParameter("table", table.toUpperCase())
				.setParameter("column", column.toUpperCase())
				.getResultList();
		for (String name : names) {
			try {
				entityManager.createNativeQuery("ALTER TABLE " + table + " DROP CONSTRAINT " + name).executeUpdate();
			} catch (Exception ex) {
				log.warn("FK {} kaldırılamadı: {}", name, ex.getMessage());
			}
		}
	}

	private void deduplicateMenuCodes(List<Object[]> rows) {
		Set<String> seen = new HashSet<>();
		for (Object[] row : rows) {
			long menuId = ((Number) row[0]).longValue();
			long saleAreaId = ((Number) row[1]).longValue();
			String code = String.valueOf(row[2]);
			if (seen.add(code)) {
				continue;
			}
			String areaCode = saleAreaRepository.findById(saleAreaId).map(SaleArea::getCode).orElse(String.valueOf(saleAreaId));
			String newCode = code + "_" + areaCode;
			int n = 2;
			while (menuPageRepository.existsByCode(newCode)) {
				newCode = code + "_" + areaCode + "_" + n++;
			}
			entityManager.createNativeQuery("UPDATE menu_pages SET code = :code WHERE id = :id")
					.setParameter("code", newCode)
					.setParameter("id", menuId)
					.executeUpdate();
		}
	}

	private void linkMenuToSaleArea(long menuId, long saleAreaId) {
		Number exists = (Number) entityManager.createNativeQuery(
				"SELECT count(*) FROM sale_area_menu_pages WHERE sale_area_id = :sa AND menu_page_id = :mp")
				.setParameter("sa", saleAreaId)
				.setParameter("mp", menuId)
				.getSingleResult();
		if (exists.longValue() == 0) {
			entityManager.createNativeQuery(
					"INSERT INTO sale_area_menu_pages (sale_area_id, menu_page_id) VALUES (:sa, :mp)")
					.setParameter("sa", saleAreaId)
					.setParameter("mp", menuId)
					.executeUpdate();
		}
	}
}

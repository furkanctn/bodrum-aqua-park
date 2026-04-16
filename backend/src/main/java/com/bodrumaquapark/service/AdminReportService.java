package com.bodrumaquapark.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.bodrumaquapark.entity.CardLedgerEntry;
import com.bodrumaquapark.entity.Product;
import com.bodrumaquapark.entity.SaleArea;
import com.bodrumaquapark.entity.TransactionType;
import com.bodrumaquapark.repository.CardLedgerEntryRepository;
import com.bodrumaquapark.util.Money;
import com.bodrumaquapark.web.dto.AdminDayCloseReportDto;
import com.bodrumaquapark.web.dto.AdminDayLedgerLineDto;
import com.bodrumaquapark.web.dto.AdminSummaryReportDto;
import com.bodrumaquapark.web.dto.LedgerTypeAggregateDto;
import com.bodrumaquapark.web.dto.ProductRevenueDto;
import com.bodrumaquapark.web.dto.SaleAreaRevenueDto;

@Service
public class AdminReportService {

	public static final ZoneId REPORT_ZONE = ZoneId.of("Europe/Istanbul");

	private final CardLedgerEntryRepository ledgerRepository;

	public AdminReportService(CardLedgerEntryRepository ledgerRepository) {
		this.ledgerRepository = ledgerRepository;
	}

	public record InstantRange(Instant fromInclusive, Instant toExclusive, LocalDate fromDay, LocalDate toDay) {
	}

	public InstantRange resolveRange(LocalDate fromInclusive, LocalDate toInclusive) {
		LocalDate to = toInclusive != null ? toInclusive : LocalDate.now(REPORT_ZONE);
		LocalDate from = fromInclusive != null ? fromInclusive : to.minusDays(6);
		if (from.isAfter(to)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Başlangıç tarihi bitişten sonra olamaz");
		}
		Instant start = from.atStartOfDay(REPORT_ZONE).toInstant();
		Instant endEx = to.plusDays(1).atStartOfDay(REPORT_ZONE).toInstant();
		return new InstantRange(start, endEx, from, to);
	}

	public InstantRange resolveSingleDay(LocalDate day) {
		LocalDate d = day != null ? day : LocalDate.now(REPORT_ZONE);
		return resolveRange(d, d);
	}

	public List<SaleAreaRevenueDto> salesBySaleArea(LocalDate from, LocalDate to) {
		InstantRange r = resolveRange(from, to);
		List<Object[]> rows = ledgerRepository.aggregateProductSalesBySaleArea(TransactionType.SALE, r.fromInclusive(),
				r.toExclusive());
		List<SaleAreaRevenueDto> out = new ArrayList<>();
		for (Object[] row : rows) {
			String code = (String) row[0];
			String name = (String) row[1];
			long cnt = ((Number) row[2]).longValue();
			BigDecimal rev = Money.normalize((BigDecimal) row[3]);
			out.add(new SaleAreaRevenueDto(code, name, cnt, rev));
		}
		return out;
	}

	public List<ProductRevenueDto> salesByProduct(LocalDate from, LocalDate to) {
		InstantRange r = resolveRange(from, to);
		List<Object[]> rows = ledgerRepository.aggregateProductSalesByProduct(TransactionType.SALE, r.fromInclusive(),
				r.toExclusive());
		List<ProductRevenueDto> out = new ArrayList<>();
		for (Object[] row : rows) {
			long pid = ((Number) row[0]).longValue();
			String name = (String) row[1];
			long cnt = ((Number) row[2]).longValue();
			BigDecimal rev = Money.normalize((BigDecimal) row[3]);
			out.add(new ProductRevenueDto(pid, name, cnt, rev));
		}
		return out;
	}

	public AdminSummaryReportDto summary(LocalDate from, LocalDate to) {
		InstantRange r = resolveRange(from, to);
		List<Object[]> rows = ledgerRepository.aggregateByTransactionType(r.fromInclusive(), r.toExclusive());
		List<LedgerTypeAggregateDto> buckets = new ArrayList<>();
		BigDecimal saleRev = BigDecimal.ZERO;
		long saleLines = 0;
		for (Object[] row : rows) {
			TransactionType type = (TransactionType) row[0];
			long cnt = ((Number) row[1]).longValue();
			BigDecimal sum = Money.normalize((BigDecimal) row[2]);
			buckets.add(new LedgerTypeAggregateDto(type, cnt, sum));
			if (type == TransactionType.SALE) {
				saleLines = cnt;
				saleRev = sum.negate();
			}
		}
		buckets.sort(Comparator.comparing(LedgerTypeAggregateDto::type));
		return new AdminSummaryReportDto(r.fromDay(), r.toDay(), REPORT_ZONE.getId(), buckets, saleRev, saleLines);
	}

	public AdminDayCloseReportDto dayClose(LocalDate date, int limit) {
		int lim = limit <= 0 ? 500 : Math.min(limit, 2000);
		InstantRange r = resolveSingleDay(date);
		AdminSummaryReportDto sum = summary(r.fromDay(), r.toDay());
		var page = ledgerRepository.findLedgerPageForRange(r.fromInclusive(), r.toExclusive(),
				PageRequest.of(0, lim, Sort.by(Sort.Direction.DESC, "createdAt")));
		List<AdminDayLedgerLineDto> lines = new ArrayList<>();
		for (CardLedgerEntry e : page.getContent()) {
			lines.add(toDayLine(e));
		}
		return new AdminDayCloseReportDto(r.fromDay(), REPORT_ZONE.getId(), sum, lines);
	}

	private static AdminDayLedgerLineDto toDayLine(CardLedgerEntry e) {
		String pname = null;
		String aname = null;
		Product p = e.getProduct();
		if (p != null) {
			pname = p.getName();
			SaleArea sa = p.getSaleArea();
			if (sa != null) {
				aname = sa.getName();
			}
		}
		String uid = e.getCard() != null ? e.getCard().getUid() : "";
		return new AdminDayLedgerLineDto(
				e.getCreatedAt(),
				e.getType(),
				Money.normalize(e.getAmountChange()),
				Money.normalize(e.getBalanceAfter()),
				pname,
				aname,
				e.getDescription(),
				maskCardUid(uid));
	}

	private static String maskCardUid(String uid) {
		if (uid == null || uid.isEmpty()) {
			return "—";
		}
		String u = uid.trim();
		if (u.length() <= 4) {
			return "****";
		}
		return "…" + u.substring(u.length() - 6);
	}
}

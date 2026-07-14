package com.bodrumaquapark.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.bodrumaquapark.entity.CardLedgerEntry;
import com.bodrumaquapark.entity.Product;
import com.bodrumaquapark.entity.TransactionType;
import com.bodrumaquapark.repository.CardLedgerEntryRepository;
import com.bodrumaquapark.util.Money;
import com.bodrumaquapark.util.TicketAgeGroupLabels;
import com.bodrumaquapark.web.dto.AdminCashRegisterDetailReportDto;
import com.bodrumaquapark.web.dto.AdminDayCloseReportDto;
import com.bodrumaquapark.web.dto.BalanceLoadReportSectionDto;
import com.bodrumaquapark.web.dto.CardInquiryRefundReportSectionDto;
import com.bodrumaquapark.web.dto.AdminDayLedgerLineDto;
import com.bodrumaquapark.web.dto.AdminSummaryReportDto;
import com.bodrumaquapark.web.dto.AgencyTicketCountDto;
import com.bodrumaquapark.web.dto.LedgerTypeAggregateDto;
import com.bodrumaquapark.web.dto.PaymentSalesReportDto;
import com.bodrumaquapark.web.dto.ProductRevenueDto;
import com.bodrumaquapark.web.dto.TicketSalesReportSectionDto;
import com.bodrumaquapark.web.dto.SaleAreaCashRegisterSectionDto;
import com.bodrumaquapark.web.dto.SaleAreaRevenueDto;

@Service
public class AdminReportService {

	public static final ZoneId REPORT_ZONE = ZoneId.of("Europe/Istanbul");

	private static final EnumSet<TransactionType> PAID_TICKET_TYPES = EnumSet.of(
			TransactionType.TICKET_CASH,
			TransactionType.TICKET_CARD);

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

	public AdminCashRegisterDetailReportDto cashRegisterDetail(LocalDate from, LocalDate to) {
		InstantRange r = resolveRange(from, to);
		List<Object[]> rows = ledgerRepository.aggregateProductSalesBySaleAreaAndProduct(TransactionType.SALE,
				r.fromInclusive(), r.toExclusive());
		Map<String, MutableSaleAreaSection> sections = new LinkedHashMap<>();
		BigDecimal totalRevenue = BigDecimal.ZERO;
		long totalLines = 0;
		for (Object[] row : rows) {
			String code = (String) row[0];
			String areaName = (String) row[1];
			long pid = ((Number) row[2]).longValue();
			String productName = (String) row[3];
			long cnt = ((Number) row[4]).longValue();
			BigDecimal rev = Money.normalize((BigDecimal) row[5]);
			MutableSaleAreaSection section = sections.computeIfAbsent(code, k -> new MutableSaleAreaSection(code, areaName));
			section.products.add(new ProductRevenueDto(pid, productName, cnt, rev));
			section.saleLineCount += cnt;
			section.revenueTry = section.revenueTry.add(rev);
			totalRevenue = totalRevenue.add(rev);
			totalLines += cnt;
		}
		List<SaleAreaCashRegisterSectionDto> sectionList = sections.values().stream()
				.map(MutableSaleAreaSection::toDto)
				.sorted(Comparator.comparing(SaleAreaCashRegisterSectionDto::revenueTry).reversed())
				.toList();
		return new AdminCashRegisterDetailReportDto(
				r.fromDay(),
				r.toDay(),
				REPORT_ZONE.getId(),
				Money.normalize(totalRevenue),
				totalLines,
				sectionList);
	}

	private static final class MutableSaleAreaSection {
		private final String code;
		private final String name;
		private long saleLineCount;
		private BigDecimal revenueTry = BigDecimal.ZERO;
		private final List<ProductRevenueDto> products = new ArrayList<>();

		private MutableSaleAreaSection(String code, String name) {
			this.code = code;
			this.name = name;
		}

		private SaleAreaCashRegisterSectionDto toDto() {
			List<ProductRevenueDto> sortedProducts = products.stream()
					.sorted(Comparator.comparing(ProductRevenueDto::revenueTry).reversed())
					.toList();
			return new SaleAreaCashRegisterSectionDto(code, name, saleLineCount, Money.normalize(revenueTry),
					sortedProducts);
		}
	}

	public PaymentSalesReportDto paymentSales(LocalDate from, LocalDate to) {
		InstantRange r = resolveRange(from, to);
		List<Object[]> rows = ledgerRepository.aggregateByTransactionType(r.fromInclusive(), r.toExclusive());
		BigDecimal cash = BigDecimal.ZERO;
		BigDecimal card = BigDecimal.ZERO;
		BigDecimal agency = BigDecimal.ZERO;
		for (Object[] row : rows) {
			TransactionType type = (TransactionType) row[0];
			BigDecimal sum = Money.normalize((BigDecimal) row[2]);
			switch (type) {
				case LOAD_CASH, TICKET_CASH -> cash = cash.add(sum);
				case LOAD_CARD, TICKET_CARD -> card = card.add(sum);
				case LOAD_AGENCY, TICKET_CREDIT -> agency = agency.add(sum);
				default -> {
				}
			}
		}
		cash = Money.normalize(cash);
		card = Money.normalize(card);
		agency = Money.normalize(agency);
		List<AgencyTicketCountDto> agencyTickets = agencyTicketCounts(r.fromInclusive(), r.toExclusive());
		long agencyTicketTotal = agencyTickets.stream().mapToLong(AgencyTicketCountDto::count).sum();
		TicketSalesReportSectionDto ticketSales = buildTicketSalesSection(r.fromInclusive(), r.toExclusive());
		CardInquiryRefundReportSectionDto cardInquiryRefund = buildCardInquiryRefundSection(r.fromInclusive(),
				r.toExclusive());
		BalanceLoadReportSectionDto balanceLoad = buildBalanceLoadSection(r.fromInclusive(), r.toExclusive());

		// Kart sorgulama iade (nakit) yapıldığında kasa nakit tutarı düşmeli.
		BigDecimal cashRefundTotal = cardInquiryRefund != null && cardInquiryRefund.cashRefundTotal() != null
				? Money.normalize(cardInquiryRefund.cashRefundTotal())
				: BigDecimal.ZERO;
		BigDecimal netCash = Money.normalize(cash.subtract(cashRefundTotal));
		BigDecimal netLoadCash = Money.normalize(balanceLoad.cashTotal().subtract(cashRefundTotal));
		BigDecimal netGrand = Money.normalize(netCash.add(card).add(agency));

		BalanceLoadReportSectionDto netBalanceLoad = new BalanceLoadReportSectionDto(
				netLoadCash,
				balanceLoad.cardTotal(),
				balanceLoad.agencyTotal());

		long ticketEntryTotal = ticketSales.totalTicketCount() + agencyTicketTotal;
		return new PaymentSalesReportDto(
				r.fromDay(), r.toDay(),
				REPORT_ZONE.getId(),
				netCash,
				card,
				agency,
				netGrand,
				agencyTickets,
				agencyTicketTotal,
				ticketEntryTotal,
				ticketSales,
				netBalanceLoad,
				cardInquiryRefund);
	}

	private TicketSalesReportSectionDto buildTicketSalesSection(Instant from, Instant to) {
		BigDecimal ticketCash = Money.normalize(ledgerRepository.sumAmountByType(TransactionType.TICKET_CASH, from, to));
		BigDecimal ticketCard = Money.normalize(ledgerRepository.sumAmountByType(TransactionType.TICKET_CARD, from, to));
		long lineQty = ledgerRepository.aggregateTicketLineQuantity(PAID_TICKET_TYPES, from, to);
		long legacyPaid = countLegacyPaidTicketsWithoutDetailLines(from, to);
		long child = 0;
		long adult = 0;
		for (Object[] row : ledgerRepository.aggregateTicketQuantitiesByAgeGroupName(PAID_TICKET_TYPES, from, to)) {
			String name = (String) row[0];
			long qty = ((Number) row[1]).longValue();
			if (qty <= 0 || TicketAgeGroupLabels.isAgencyTariff(name)) {
				continue;
			}
			if (TicketAgeGroupLabels.isChildTariff(name)) {
				child += qty;
			} else {
				adult += qty;
			}
		}
		long total = lineQty + legacyPaid;
		long classified = child + adult;
		if (classified < lineQty) {
			adult += lineQty - classified;
		}
		adult += legacyPaid;
		return new TicketSalesReportSectionDto(ticketCash, ticketCard, total, child, adult);
	}

	private long countLegacyPaidTicketsWithoutDetailLines(Instant from, Instant to) {
		List<CardLedgerEntry> payments = ledgerRepository.findLegacyPaidTicketPaymentEntries(PAID_TICKET_TYPES, from, to);
		long count = 0;
		for (CardLedgerEntry payment : payments) {
			Instant lineWindowEnd = payment.getCreatedAt().plusSeconds(30);
			long detailLines = ledgerRepository.countPaidNonAgencyTicketLinesOnCard(
					payment.getCard().getId(),
					PAID_TICKET_TYPES,
					payment.getCreatedAt(),
					lineWindowEnd);
			if (detailLines == 0) {
				count++;
			}
		}
		return count;
	}

	private BalanceLoadReportSectionDto buildBalanceLoadSection(Instant from, Instant to) {
		BigDecimal loadCash = Money.normalize(ledgerRepository.sumAmountByType(TransactionType.LOAD_CASH, from, to));
		BigDecimal loadCard = Money.normalize(ledgerRepository.sumAmountByType(TransactionType.LOAD_CARD, from, to));
		BigDecimal loadAgency = Money.normalize(ledgerRepository.sumAmountByType(TransactionType.LOAD_AGENCY, from, to));
		return new BalanceLoadReportSectionDto(loadCash, loadCard, loadAgency);
	}

	private CardInquiryRefundReportSectionDto buildCardInquiryRefundSection(Instant from, Instant to) {
		BigDecimal cashRefund = Money.normalize(ledgerRepository.sumNegativeAmountByType(TransactionType.REFUND_CASH,
				from, to));
		long refundCount = ledgerRepository.countNegativeAmountByType(TransactionType.REFUND_CASH, from, to);
		long clearCount = ledgerRepository.countByTypeAndDescriptionPrefix(
				TransactionType.DAILY_RESET,
				CardService.INQUIRY_FORFEIT_DESC_PREFIX + "%",
				from,
				to);
		return new CardInquiryRefundReportSectionDto(cashRefund, refundCount + clearCount);
	}

	private List<AgencyTicketCountDto> agencyTicketCounts(Instant from, Instant to) {
		List<Object[]> rows = ledgerRepository.aggregateAgencyTicketCounts(from, to);
		List<AgencyTicketCountDto> out = new ArrayList<>();
		for (Object[] row : rows) {
			String name = (String) row[0];
			long cnt = ((Number) row[1]).longValue();
			if (cnt > 0) {
				out.add(new AgencyTicketCountDto(name, cnt));
			}
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
		}
		if (e.getSaleArea() != null) {
			aname = e.getSaleArea().getName();
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

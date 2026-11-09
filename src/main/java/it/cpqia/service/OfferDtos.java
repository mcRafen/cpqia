package it.cpqia.service;

import it.cpqia.extras.ExtraModel;
import it.cpqia.pricing.PricingModel.Row;
import it.cpqia.terms.TermsModel;
import it.cpqia.pricing.RuleChecker.Violation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** DTO scambiati con il wizard "Nuova offerta" (JSON). */
public final class OfferDtos {

    private OfferDtos() {
    }

    public record ModuleQty(Long moduleId, Integer qty) {
    }

    public record LineRequest(Long applicationId, String modeCode, Map<Long, Integer> drivers, List<ModuleQty> modules) {
    }

    public record OfferRequest(Long id, LocalDate offerDate, Integer progressive, Long customerId, String projectTitle,
                               String contactRef, BigDecimal discountPct, List<LineRequest> lines,
                               Boolean optMaintenanceAdditional, Boolean optExtraServices, Boolean optFinalSummary,
                               ExtraModel.Selection extras, TermsModel.PaymentTerms terms,
                               BigDecimal distanceKm, String nearestSede, Long travelRateId,
                               List<ActivityReq> activities, BigDecimal activityDiscountPct, Boolean optActivityDetail,
                               Boolean maintOnNet, Boolean optPowerSystem, Long galileoApplicationId, Boolean optSmitechServices,
                               Boolean optDepartmental, Long powerModelId, Integer powerRamGb, String powerDisk, Boolean powerLto8,
                               Boolean powerEth10, Integer powerIbmiUsers, Boolean powerIbmiUnlimited,
                               String powerCable, String powerTransfer, String powerTransferRef, String powerEdition, String powerOs,
                               Integer powerMaintMonths, BigDecimal powerListPrice, BigDecimal powerNetPrice,
                               List<PowerProductSel> powerProducts, Integer powerDiskCount, Boolean optUseRfq, Boolean optServicesDays, String appTotalsMode, BigDecimal rfqSheetTotal,
                               it.cpqia.galileo.GalileoService.Request galileo, String servicesMode,
                               List<ProfileRateReq> profileRates, List<PowerMaintReq> powerMaint, Boolean optHoursDetail,
                               LocalDate activityDiscountUntil) {
    }

    /** importi di un periodo di manutenzione SW/HW (36, 48, 60 mesi) per la sezione POWER o LTO8 */
    public record PowerMaintReq(Integer months, String kind, BigDecimal listPrice, BigDecimal netPrice) {
    }

    /** tariffa oraria di un profilo professionale modificata nell'offerta */
    public record ProfileRateReq(Long profileId, BigDecimal amount) {
    }

    /** valore scelto per un prodotto IBM i (1, 2, 3, 4 o MIGRATO); code e description sono in uscita */
    public record PowerProductSel(Long productId, String value, String code, String description) {
    }

    /** riga di attivita' sistemistica; rate e profileName sono in uscita (il server rilegge la tariffa dal profilo) */
    public record ActivityReq(Long applicationId, String description, Long profileId, BigDecimal hours, BigDecimal rate, String profileName,
                          String rfqGroup, Boolean rfqImport, BigDecimal days) {
    }

    public record LineResult(Long applicationId, BigDecimal license, BigDecimal onetime, BigDecimal licenseTotal,
                             BigDecimal maintenance, BigDecimal rentMonthly, List<Row> rows, BigDecimal annual) {
    }

    public record CalcResponse(List<LineResult> lines, BigDecimal totalLicense, BigDecimal totalMaintenance,
                               BigDecimal discountPct, BigDecimal netLicense, BigDecimal netMaintenance,
                               BigDecimal commercialMargin, ExtraModel.Totals extraTotals, List<Violation> violations) {
    }

    /** Header dell'offerta per la riapertura in modifica */
    public record OfferHeader(Long id, String number, LocalDate offerDate, Integer progressive, String initials,
                              Long customerId, String projectTitle, String contactRef, BigDecimal discountPct,
                              String status, boolean optMaintenanceAdditional, boolean optExtraServices,
                              boolean optFinalSummary, BigDecimal distanceKm, String nearestSede, Long travelRateId,
                              BigDecimal activityDiscountPct, boolean optActivityDetail, boolean maintOnNet,
                              boolean optPowerSystem, Long galileoApplicationId, boolean optSmitechServices, boolean optDepartmental,
                              Long powerModelId, Integer powerRamGb, String powerDisk, boolean powerLto8, boolean powerEth10,
                              Integer powerIbmiUsers, boolean powerIbmiUnlimited,
                              String powerCable, String powerTransfer, String powerTransferRef, String powerEdition, String powerOs,
                              int powerMaintMonths, BigDecimal powerListPrice, BigDecimal powerNetPrice, int powerDiskCount, boolean optUseRfq, boolean optServicesDays, String appTotalsMode, BigDecimal rfqSheetTotal, String servicesMode) {
    }
}

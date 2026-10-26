package ma.facture.invoicing.invoice.service;

import ma.facture.invoicing.client.entity.Client;
import ma.facture.invoicing.client.repository.ClientRepository;
import ma.facture.invoicing.contract.entity.*;
import ma.facture.invoicing.contract.repository.ContractRepository;
import ma.facture.invoicing.invoice.calculation.InvoiceCalculationService;
import ma.facture.invoicing.invoice.dto.*;
import ma.facture.invoicing.invoice.entity.*;
import ma.facture.invoicing.invoice.exception.*;
import ma.facture.invoicing.invoice.mapper.InvoiceMapper;
import ma.facture.invoicing.invoice.numbering.InvoiceNumberGenerator;
import ma.facture.invoicing.invoice.pdf.*;
import ma.facture.invoicing.invoice.repository.InvoiceRepository;
import ma.facture.invoicing.product.entity.*;
import ma.facture.invoicing.product.repository.ProductRepository;
import ma.facture.invoicing.security.AuthenticatedUser;
import ma.facture.invoicing.user.UserRole;
import ma.facture.invoicing.company.TenantDefaults;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InvoiceServiceTest {
    private static final UUID ACTOR_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final LocalDate INVOICE_DATE = LocalDate.of(2026, 8, 4);
    private static final Instant NOW = Instant.parse("2026-08-04T10:15:30Z");

    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final ClientRepository clients = mock(ClientRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final ContractRepository contracts = mock(ContractRepository.class);
    private final InvoiceNumberGenerator numbers = mock(InvoiceNumberGenerator.class);
    private final InvoicePdfService pdf = mock(InvoicePdfService.class);
    private final InvoiceMapper mapper = new InvoiceMapper(mock(ma.facture.invoicing.user.UserRepository.class));
    private final AuthenticatedUser actor = new AuthenticatedUser(ACTOR_ID, "manager@example.com", UserRole.INVOICE_MANAGER);
    private final Client client = new Client("CLI-000001", "Atlas Client", "0500", "Old address", null, "001527304000014", ACTOR_ID);
    private final Product product = new Product("PRD-000001", "Blue Roll", "Original description",
            decimal("13.00"), decimal("20.00"), ProductUnit.ROLL, ACTOR_ID);
    private InvoiceService service;

    @BeforeEach void setUp() {
        service = new InvoiceService(invoices, clients, products, contracts, new InvoiceCalculationService(), numbers, pdf,
                mapper, Clock.fixed(NOW, ZoneOffset.UTC), mock(ma.facture.invoicing.invoice.settlement.InvoicePaymentRepository.class));
        when(clients.findByIdAndCompanyId(client.getId(), TenantDefaults.DEFAULT_COMPANY_ID)).thenReturn(Optional.of(client));
        when(products.findByIdAndCompanyId(product.getId(), TenantDefaults.DEFAULT_COMPANY_ID)).thenReturn(Optional.of(product));
        when(invoices.saveAndFlush(any(Invoice.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pdf.generate(any(Invoice.class))).thenReturn(new GeneratedInvoicePdf("2026/FA-2026-000001.pdf", "Treize dirhams"));
    }

    @Test void createsStandaloneDraftWithAuthoritativeRoundedTotalsAndNoNumber() {
        InvoiceResponse response = service.create(standaloneRequest(List.of(line(product.getId(), "2", "13.005", "19.995", "5"))), actor);

        assertThat(response.status()).isEqualTo(InvoiceStatus.DRAFT);
        assertThat(response.invoiceNumber()).isNull();
        assertThat(response.customerOrderReference()).isEqualTo("ORDER-1");
        assertThat(response.subtotalExcludingTax()).isEqualByComparingTo("26.02");
        assertThat(response.totalDiscount()).isEqualByComparingTo("1.30");
        assertThat(response.taxableAmount()).isEqualByComparingTo("24.72");
        assertThat(response.totalVat()).isEqualByComparingTo("4.94");
        assertThat(response.totalIncludingTax()).isEqualByComparingTo("29.66");
        assertThat(response.pdfAvailable()).isFalse();
        assertThat(response.pdfDownloadUrl()).isNull();

        InvoiceSummaryResponse summary = mapper.toSummary(savedInvoice());
        assertThat(summary.subtotalExcludingTax()).isEqualByComparingTo("26.02");
        assertThat(summary.totalDiscount()).isEqualByComparingTo("1.30");
        assertThat(summary.taxableAmount()).isEqualByComparingTo("24.72");
        assertThat(summary.totalVat()).isEqualByComparingTo("4.94");
        assertThat(summary.totalIncludingTax()).isEqualByComparingTo("29.66");
    }

    @Test void contractAutomaticallyControlsPaymentPricingRatesDiscountAndMinimumQuantity() {
        Contract contract = activeContract(client, product, decimal("10.00"), decimal("20.00"), decimal("7.00"), decimal("2.000"));
        when(contracts.findDetailedByIdAndCompanyId(contract.getId(), TenantDefaults.DEFAULT_COMPANY_ID)).thenReturn(Optional.of(contract));
        InvoiceCreateRequest request = new InvoiceCreateRequest(INVOICE_DATE, client.getId(), contract.getId(), PaymentMethod.CASH,
                null, List.of(line(product.getId(), "2", "999", "0", "0")));

        InvoiceResponse response = service.create(request, actor);

        assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.CHECK);
        assertThat(response.lines().getFirst().unitPriceExcludingTax()).isEqualByComparingTo("10.00");
        assertThat(response.lines().getFirst().vatRate()).isEqualByComparingTo("20.00");
        assertThat(response.lines().getFirst().discountRate()).isEqualByComparingTo("7.00");
        assertThatThrownBy(() -> service.create(new InvoiceCreateRequest(INVOICE_DATE, client.getId(), contract.getId(), null,
                null, List.of(line(product.getId(), "1.999", null, null, null))), actor))
                .isInstanceOf(InvoiceValidationException.class).hasMessageContaining("minimum");
    }

    @Test void acceptsBankTransferForNewStandaloneInvoices() {
        InvoiceCreateRequest request = new InvoiceCreateRequest(INVOICE_DATE, client.getId(), null,
                PaymentMethod.BANK_TRANSFER, null, List.of());

        InvoiceResponse response = service.create(request, actor);

        assertThat(response.paymentMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
    }

    @Test void rejectsUnsupportedPaymentMethodsForNewStandaloneInvoices() {
        InvoiceCreateRequest request = new InvoiceCreateRequest(INVOICE_DATE, client.getId(), null,
                PaymentMethod.CARD, null, List.of());

        assertThatThrownBy(() -> service.create(request, actor))
                .isInstanceOf(InvoiceValidationException.class)
                .hasMessage("Le mode de paiement doit être Espèces, Chèque ou Virement bancaire");
    }

    @Test void validationRecalculatesCapturesSnapshotsNumbersAndFreezesBusinessData() {
        InvoiceResponse draft = service.create(standaloneRequest(List.of(line(product.getId(), "1", "13", "20", null))), actor);
        Invoice entity = savedInvoice();
        when(invoices.findDetailedByIdAndCompanyId(draft.id(), TenantDefaults.DEFAULT_COMPANY_ID)).thenReturn(Optional.of(entity));
        when(numbers.generateForValidation(TenantDefaults.DEFAULT_COMPANY_ID)).thenReturn("FA-2026-000001");

        InvoiceResponse validated = service.validate(draft.id(), new InvoiceVersionRequest(0), actor);
        client.update("Changed Client", null, "New address", null, null, ACTOR_ID);
        product.update("Changed Product", "Changed description", decimal("99"), decimal("0"), ProductUnit.OTHER, ACTOR_ID);
        InvoiceResponse historical = mapper.toResponse(entity);

        assertThat(validated.status()).isEqualTo(InvoiceStatus.VALIDATED);
        assertThat(validated.invoiceNumber()).isEqualTo("FA-2026-000001");
        assertThat(validated.validatorUserId()).isEqualTo(ACTOR_ID);
        assertThat(validated.validatedAt()).isEqualTo(NOW);
        assertThat(validated.pdfAvailable()).isTrue();
        assertThat(validated.pdfDownloadUrl()).isEqualTo("/api/v1/invoices/" + draft.id() + "/pdf");
        assertThat(historical.client().clientName()).isEqualTo("Atlas Client");
        assertThat(historical.client().address()).isEqualTo("Old address");
        assertThat(historical.lines().getFirst().productName()).isEqualTo("Blue Roll");
        assertThat(historical.lines().getFirst().productDescription()).isEqualTo("Original description");
        assertThatThrownBy(() -> service.update(draft.id(), updateRequest(0), actor))
                .isInstanceOf(InvoiceConflictException.class).hasMessageContaining("BROUILLON");
        verify(numbers).generateForValidation(TenantDefaults.DEFAULT_COMPANY_ID);
    }

    @Test void validatesActiveResourcesLinesVersionAndContractRelationship() {
        InvoiceResponse emptyDraft = service.create(standaloneRequest(List.of()), actor);
        Invoice empty = savedInvoice();
        when(invoices.findDetailedByIdAndCompanyId(emptyDraft.id(), TenantDefaults.DEFAULT_COMPANY_ID)).thenReturn(Optional.of(empty));
        assertThatThrownBy(() -> service.validate(emptyDraft.id(), new InvoiceVersionRequest(0), actor))
                .isInstanceOf(InvoiceValidationException.class).hasMessageContaining("une ligne");

        InvoiceResponse populatedDraft = service.create(standaloneRequest(List.of(line(product.getId(), "1", "1", "0", null))), actor);
        Invoice populated = savedInvoice();
        when(invoices.findDetailedByIdAndCompanyId(populatedDraft.id(), TenantDefaults.DEFAULT_COMPANY_ID)).thenReturn(Optional.of(populated));
        assertThatThrownBy(() -> service.validate(populatedDraft.id(), new InvoiceVersionRequest(9), actor))
                .isInstanceOf(InvoiceConcurrencyException.class);
        client.setStatus(false, ACTOR_ID);
        assertThatThrownBy(() -> service.validate(populatedDraft.id(), new InvoiceVersionRequest(0), actor))
                .isInstanceOf(InvoiceConflictException.class).hasMessageContaining("client");
        verifyNoInteractions(numbers);
    }

    @Test void rejectsIncompleteDuplicateAndInvalidContractDrafts() {
        assertThatThrownBy(() -> service.create(new InvoiceCreateRequest(INVOICE_DATE, client.getId(), null, null, null, List.of()), actor))
                .isInstanceOf(InvoiceValidationException.class).hasMessageContaining("mode de paiement");
        assertThatThrownBy(() -> service.create(standaloneRequest(List.of(line(product.getId(), "1", null, "20", null))), actor))
                .isInstanceOf(InvoiceValidationException.class).hasMessageContaining("prix unitaire");
        InvoiceLineRequest duplicate = line(product.getId(), "1", "1", "20", null);
        assertThatThrownBy(() -> service.create(standaloneRequest(List.of(duplicate, duplicate)), actor))
                .isInstanceOf(InvoiceValidationException.class).hasMessageContaining("une seule fois");

        Client other = new Client("CLI-000002", "Other", null, null, null, null, ACTOR_ID);
        Contract foreign = activeContract(other, product, decimal("1"), decimal("20"), decimal("0"), null);
        when(contracts.findDetailedByIdAndCompanyId(foreign.getId(), TenantDefaults.DEFAULT_COMPANY_ID)).thenReturn(Optional.of(foreign));
        assertThatThrownBy(() -> service.create(new InvoiceCreateRequest(INVOICE_DATE, client.getId(), foreign.getId(), null, null, List.of()), actor))
                .isInstanceOf(InvoiceValidationException.class).hasMessageContaining("client sélectionné");
    }

    @Test void followsPaidAndCancelledLifecycleWithoutReallocatingNumbers() {
        InvoiceResponse draft = service.create(standaloneRequest(List.of(line(product.getId(), "1", "10", "20", null))), actor);
        Invoice invoice = savedInvoice();
        when(invoices.findDetailedByIdAndCompanyId(draft.id(), TenantDefaults.DEFAULT_COMPANY_ID)).thenReturn(Optional.of(invoice));
        when(numbers.generateForValidation(TenantDefaults.DEFAULT_COMPANY_ID)).thenReturn("FA-2026-000007", "FA-2026-000008");
        when(pdf.generate(any(Invoice.class))).thenAnswer(invocation -> {
            Invoice value = invocation.getArgument(0);
            return new GeneratedInvoicePdf("2026/" + value.getInvoiceNumber() + ".pdf", "Douze dirhams");
        });
        service.validate(draft.id(), new InvoiceVersionRequest(0), actor);

        InvoiceResponse paid = service.markPaid(draft.id(), new InvoiceVersionRequest(0), actor);
        assertThat(paid.status()).isEqualTo(InvoiceStatus.PAID);
        assertThat(service.markPaid(draft.id(), new InvoiceVersionRequest(99), actor).status()).isEqualTo(InvoiceStatus.PAID);
        assertThatThrownBy(() -> service.cancel(draft.id(), new InvoiceVersionRequest(0), actor))
                .isInstanceOf(InvoiceConflictException.class);

        InvoiceResponse cancellableDraft = service.create(standaloneRequest(List.of(line(product.getId(), "1", "10", "20", null))), actor);
        Invoice cancellable = savedInvoice();
        when(invoices.findDetailedByIdAndCompanyId(cancellableDraft.id(), TenantDefaults.DEFAULT_COMPANY_ID)).thenReturn(Optional.of(cancellable));
        service.validate(cancellableDraft.id(), new InvoiceVersionRequest(0), actor);
        InvoiceResponse cancelled = service.cancel(cancellableDraft.id(), new InvoiceVersionRequest(0), actor);
        assertThat(cancelled.status()).isEqualTo(InvoiceStatus.CANCELLED);
        assertThat(cancelled.invoiceNumber()).isEqualTo("FA-2026-000008");
        assertThat(cancelled.cancelledAt()).isEqualTo(NOW);
        assertThat(service.cancel(cancellableDraft.id(), new InvoiceVersionRequest(99), actor).status()).isEqualTo(InvoiceStatus.CANCELLED);
        verify(numbers, times(2)).generateForValidation(TenantDefaults.DEFAULT_COMPANY_ID);
    }

    @Test void rejectsInvalidListQueriesAndSorts() {
        assertThatThrownBy(() -> service.list(-1, 20, null, null, null, null, null, null, null)).isInstanceOf(InvalidInvoiceQueryException.class);
        assertThatThrownBy(() -> service.list(0, 101, null, null, null, null, null, null, null)).isInstanceOf(InvalidInvoiceQueryException.class);
        assertThatThrownBy(() -> service.list(0, 20, null, null, null, null, null, INVOICE_DATE, INVOICE_DATE.minusDays(1))).isInstanceOf(InvalidInvoiceQueryException.class);
        assertThatThrownBy(() -> service.list(0, 20, "client,asc", null, null, null, null, null, null)).isInstanceOf(InvalidInvoiceQueryException.class);
        assertThatThrownBy(() -> service.list(0, 20, "createdAt,sideways", null, null, null, null, null, null)).isInstanceOf(InvalidInvoiceQueryException.class);
        assertThatThrownBy(() -> service.list(0, 20, null, null, null, null, "unknown", null, null)).isInstanceOf(InvalidInvoiceQueryException.class);
        assertThat(InvoiceService.parseSort("invoiceDate,asc").getOrderFor("invoiceDate")).isNotNull();
        assertThat(InvoiceService.normalizeSearch("  FA-1 ")).isEqualTo("fa-1");
    }

    @Test void translatesPersistenceRaceToInvoiceSpecificConcurrencyConflict() {
        when(invoices.saveAndFlush(any(Invoice.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Invoice.class, UUID.randomUUID()));

        assertThatThrownBy(() -> service.create(standaloneRequest(List.of()), actor))
                .isInstanceOf(InvoiceConcurrencyException.class).hasMessageContaining("facture");
    }

    @Test void abortsValidationWhenPdfGenerationFailsBeforeFinalPersistence() {
        InvoiceResponse draft = service.create(standaloneRequest(List.of(line(product.getId(), "1", "10", "20", null))), actor);
        Invoice invoice = savedInvoice();
        when(invoices.findDetailedByIdAndCompanyId(draft.id(), TenantDefaults.DEFAULT_COMPANY_ID)).thenReturn(Optional.of(invoice));
        when(numbers.generateForValidation(TenantDefaults.DEFAULT_COMPANY_ID)).thenReturn("FA-2026-000009");
        when(pdf.generate(invoice)).thenThrow(new InvoicePdfException("disk unavailable"));

        assertThatThrownBy(() -> service.validate(draft.id(), new InvoiceVersionRequest(0), actor))
                .isInstanceOf(InvoicePdfException.class);
        verify(invoices, times(1)).saveAndFlush(any(Invoice.class));
    }

    private Invoice savedInvoice() { return mockingDetails(invoices).getInvocations().stream()
            .filter(invocation -> invocation.getMethod().getName().equals("saveAndFlush"))
            .reduce((first, second) -> second).orElseThrow().getArgument(0); }

    private InvoiceCreateRequest standaloneRequest(List<InvoiceLineRequest> lines) {
        return new InvoiceCreateRequest(INVOICE_DATE, client.getId(), null, PaymentMethod.CASH, " ORDER-1 ", lines);
    }

    private InvoiceUpdateRequest updateRequest(long version) {
        return new InvoiceUpdateRequest(INVOICE_DATE, client.getId(), null, PaymentMethod.CHECK, null, List.of(), version);
    }

    private static InvoiceLineRequest line(UUID productId, String quantity, String price, String vat, String discount) {
        return new InvoiceLineRequest(productId, decimal(quantity), decimal(price), decimal(vat), decimal(discount));
    }

    private static Contract activeContract(Client client, Product product, BigDecimal price, BigDecimal vat,
                                           BigDecimal discount, BigDecimal minimum) {
        Contract contract = new Contract("CTR-2026-000001", client, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                PaymentMethod.CHECK, BigDecimal.ZERO.setScale(2), null, ACTOR_ID);
        contract.addLine(product, price, vat, discount, minimum, null);
        contract.activate(ACTOR_ID, NOW);
        return contract;
    }

    private static BigDecimal decimal(String value) { return value == null ? null : new BigDecimal(value); }
}

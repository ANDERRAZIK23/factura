package ma.facture.invoicing.contract.service;

import ma.facture.invoicing.client.entity.Client;
import ma.facture.invoicing.contract.entity.*;
import ma.facture.invoicing.contract.exception.*;
import ma.facture.invoicing.product.entity.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class ContractValidationServiceTest {
    private final ContractValidationService validation = new ContractValidationService(new ContractPricingService());
    private final UUID actor = UUID.randomUUID();

    @Test void validatesDatesDefaultsAndNotes() {
        validation.validateDates(LocalDate.of(2026, 1, 1), null);
        validation.validateDates(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
        assertThat(validation.defaultDiscount(null)).isEqualByComparingTo("0.00");
        assertThat(ContractValidationService.notes("  note  ", 10, "Notes")).isEqualTo("note");
        assertThat(ContractValidationService.notes(null, 10, "Notes")).isNull();
        assertThat(ContractValidationService.notes("   ", 10, "Notes")).isNull();
        assertThatThrownBy(() -> validation.validateDates(null, null)).isInstanceOf(ContractValidationException.class);
        assertThatThrownBy(() -> validation.validateDates(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1))).isInstanceOf(ContractValidationException.class);
        assertThatThrownBy(() -> validation.defaultDiscount(new BigDecimal("101"))).isInstanceOf(ContractValidationException.class);
        assertThatThrownBy(() -> ContractValidationService.notes("too long", 3, "Notes")).isInstanceOf(ContractValidationException.class);
    }

    @Test void validatesActiveClientForDraft() {
        Client client = client(); validation.validateClientForDraft(client);
        client.setStatus(false, actor);
        assertThatThrownBy(() -> validation.validateClientForDraft(client)).isInstanceOf(ContractValidationException.class);
        assertThatThrownBy(() -> validation.validateClientForDraft(null)).isInstanceOf(ContractValidationException.class);
    }

    @Test void validatesActivationAndUsabilityBoundaries() {
        Client client = client(); Product product = product();
        Contract contract = contract(client, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 8, 3));
        assertThat(validation.usable(contract, LocalDate.of(2026, 8, 3))).isFalse();
        assertThatThrownBy(() -> validation.validateActivation(contract, LocalDate.of(2026, 8, 3))).isInstanceOf(ContractConflictException.class).hasMessageContaining("au moins un");
        contract.addLine(product, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, null, null);
        validation.validateActivation(contract, LocalDate.of(2026, 8, 3));
        contract.activate(actor, java.time.Instant.EPOCH);
        assertThat(validation.usable(contract, LocalDate.of(2026, 8, 3))).isTrue();
        assertThat(validation.usable(contract, LocalDate.of(2025, 12, 31))).isFalse();
        assertThat(validation.usable(contract, LocalDate.of(2026, 8, 4))).isFalse();
        Contract openEnded = contract(client, LocalDate.of(2026, 1, 1), null);
        openEnded.addLine(product(), BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, null, null);
        openEnded.activate(actor, java.time.Instant.EPOCH);
        assertThat(validation.usable(openEnded, LocalDate.of(2099, 1, 1))).isTrue();
        product.setStatus(false, actor);
        Contract inactiveProduct = contract(client, LocalDate.of(2026, 1, 1), null);
        inactiveProduct.addLine(product, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, null, null);
        assertThatThrownBy(() -> validation.validateActivation(inactiveProduct, LocalDate.of(2026, 8, 3))).isInstanceOf(ContractConflictException.class).hasMessageContaining("produits");
        client.setStatus(false, actor);
        assertThatThrownBy(() -> validation.validateActivation(inactiveProduct, LocalDate.of(2026, 8, 3))).isInstanceOf(ContractConflictException.class).hasMessageContaining("client");
        Client active = client(); Contract past = contract(active, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31));
        past.addLine(product(), BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, null, null);
        assertThatThrownBy(() -> validation.validateActivation(past, LocalDate.of(2026, 1, 1))).isInstanceOf(ContractConflictException.class).hasMessageContaining("expiré");
    }

    private Client client() { return new Client("CLI-000001", "Client", null, null, null, null, actor); }
    private Product product() { return new Product("PRD-000001", "Product", null, BigDecimal.ONE, new BigDecimal("20"), ProductUnit.PIECE, actor); }
    private Contract contract(Client client, LocalDate start, LocalDate end) { return new Contract("CTR-2026-000001", client, start, end, PaymentMethod.CHECK, BigDecimal.ZERO, null, actor); }
}

package ma.facture.invoicing.invoice.mapper;

import ma.facture.invoicing.client.entity.Client;
import ma.facture.invoicing.contract.entity.PaymentMethod;
import ma.facture.invoicing.invoice.entity.Invoice;
import ma.facture.invoicing.user.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InvoiceMapperTest {
    private final UserRepository users = mock(UserRepository.class);
    private final InvoiceMapper mapper = new InvoiceMapper(users);

    @Test void resolvesActualCreatorEditorAndValidatorNamesIncludingDisabledUsers() {
        UserEntity creator = new UserEntity("Samira Bennani", "creator@example.com", "hash", UserRole.INVOICE_MANAGER);
        UserEntity editor = new UserEntity("Youssef Amrani", "editor@example.com", "hash", UserRole.INVOICE_MANAGER);
        UserEntity validator = new UserEntity("Nadia El Alami", "validator@example.com", "hash", UserRole.ADMIN);
        editor.setEnabled(false);
        Invoice invoice = invoice(creator.getId());
        invoice.updateDraft(invoice.getInvoiceDate(), invoice.getClient(), null, PaymentMethod.CASH, null, editor.getId());
        // Validation records the validator as the last editor as well.
        invoice.validate("FA-2026-000001", validator.getId(), Instant.parse("2026-10-19T10:00:00Z"));
        invoice.markPaid(editor.getId());
        when(users.findAllById(any())).thenReturn(List.of(creator, editor, validator));

        var response = mapper.toResponse(invoice);

        assertThat(response.createdBy()).isEqualTo(creator.getId());
        assertThat(response.createdByName()).isEqualTo("Samira Bennani");
        assertThat(response.updatedByName()).isEqualTo("Youssef Amrani");
        assertThat(response.validatorName()).isEqualTo("Nadia El Alami");
        verify(users).findAllById(Set.of(creator.getId(), editor.getId(), validator.getId()));
    }
    @Test void keepsAuditIdsWithoutInventingNamesWhenAnActorCannotBeFound() {
        UUID creator = UUID.randomUUID();
        when(users.findAllById(any())).thenReturn(List.of());
        var response = mapper.toResponse(invoice(creator));
        assertThat(response.createdBy()).isEqualTo(creator);
        assertThat(response.createdByName()).isNull();
        assertThat(response.updatedByName()).isNull();
        assertThat(response.validatorName()).isNull();
    }
    private static Invoice invoice(UUID actor) {
        Client client = new Client("CLI-000001", "Atlas Client", null, null, null, null, actor);
        return new Invoice(LocalDate.of(2026, 10, 19), client, null, PaymentMethod.CASH, null, actor);
    }
}

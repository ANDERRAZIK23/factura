package ma.facture.invoicing.client.dto;

import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import static org.assertj.core.api.Assertions.*;

class ClientRequestValidationTest {
    private static jakarta.validation.Validator validator;
    @BeforeAll static void validator() { validator = Validation.buildDefaultValidatorFactory().getValidator(); }

    @Test void acceptsOptionalBlankValuesAndSpacedIce() {
        var request = new ClientCreateRequest("Acme", "+212 (5) 00-00", "", "", "001 527 304 000 014");
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test void rejectsBlankOrTooShortNameInvalidPhoneEmailAndIce() {
        assertThat(validator.validate(new ClientCreateRequest(" ", "12x", null, "bad", "123")))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("clientName", "phoneNumber", "email", "ice");
        assertThat(validator.validate(new ClientCreateRequest(" A ", null, null, null, null)))
                .extracting(violation -> violation.getPropertyPath().toString()).contains("clientName");
    }
}

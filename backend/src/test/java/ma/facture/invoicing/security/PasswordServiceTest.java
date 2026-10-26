package ma.facture.invoicing.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.assertj.core.api.Assertions.*;

class PasswordServiceTest {
    @Test void hashesAndMatchesWithoutRetainingPlainText() {
        var service = new PasswordService(new BCryptPasswordEncoder(4)); var hash = service.hash("StrongPassword123!");
        assertThat(hash).doesNotContain("StrongPassword123!"); assertThat(service.matches("StrongPassword123!", hash)).isTrue();
        assertThat(service.matches("wrong", hash)).isFalse();
    }
}


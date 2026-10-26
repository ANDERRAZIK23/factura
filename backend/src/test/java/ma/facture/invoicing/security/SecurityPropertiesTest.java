package ma.facture.invoicing.security;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class SecurityPropertiesTest {
    private static final String SECRET = "a".repeat(64);
    @Test void acceptsSecureConfiguration() { assertThat(valid().allowedOrigins()).containsExactly("http://localhost:5173"); }
    @Test void rejectsShortSecret() { assertThatThrownBy(() -> create("short","issuer","aud",Duration.ofMinutes(1),Duration.ofDays(1))).isInstanceOf(IllegalArgumentException.class); }
    @Test void rejectsNullSecretIssuerAndAudience() {
        assertThatThrownBy(() -> create(null,"issuer","aud",Duration.ofMinutes(1),Duration.ofDays(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> create(SECRET,null,"aud",Duration.ofMinutes(1),Duration.ofDays(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> create(SECRET,"issuer",null,Duration.ofMinutes(1),Duration.ofDays(1))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsBlankIssuer() { assertThatThrownBy(() -> create(SECRET," ","aud",Duration.ofMinutes(1),Duration.ofDays(1))).isInstanceOf(IllegalArgumentException.class); }
    @Test void rejectsBlankAudience() { assertThatThrownBy(() -> create(SECRET,"issuer"," ",Duration.ofMinutes(1),Duration.ofDays(1))).isInstanceOf(IllegalArgumentException.class); }
    @Test void rejectsInvalidExpirations() {
        assertThatThrownBy(() -> create(SECRET,"issuer","aud",null,Duration.ofDays(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> create(SECRET,"issuer","aud",Duration.ofMinutes(-1),Duration.ofDays(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> create(SECRET,"issuer","aud",Duration.ZERO,Duration.ofDays(1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> create(SECRET,"issuer","aud",Duration.ofMinutes(1),null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> create(SECRET,"issuer","aud",Duration.ofMinutes(1),Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> create(SECRET,"issuer","aud",Duration.ofMinutes(1),Duration.ofDays(-1))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void convertsNullAllowedOriginsToEmptyList() {
        var properties = new SecurityProperties(SECRET,"issuer","aud",Duration.ofMinutes(1),Duration.ofDays(1),null,false,"","","");
        assertThat(properties.allowedOrigins()).isEmpty();
    }
    private SecurityProperties valid(){ return create(SECRET,"issuer","aud",Duration.ofMinutes(1),Duration.ofDays(1)); }
    private SecurityProperties create(String secret,String issuer,String audience,Duration access,Duration refresh){ return new SecurityProperties(secret,issuer,audience,access,refresh,List.of("http://localhost:5173"),false,"","",""); }
}

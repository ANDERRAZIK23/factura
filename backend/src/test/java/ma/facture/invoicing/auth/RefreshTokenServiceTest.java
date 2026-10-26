package ma.facture.invoicing.auth;

import ma.facture.invoicing.common.AuthenticationException;
import ma.facture.invoicing.security.SecurityProperties;
import ma.facture.invoicing.user.*;
import org.junit.jupiter.api.*;
import org.mockito.*;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RefreshTokenServiceTest {
    private final Instant now = Instant.parse("2026-08-03T00:00:00Z");
    private RefreshTokenRepository repository; private RefreshTokenService service; private UserEntity user;
    @BeforeEach void setup() {
        repository = mock(RefreshTokenRepository.class); var random = mock(SecureRandom.class);
        doAnswer(invocation -> { byte[] target = invocation.getArgument(0); Arrays.fill(target, (byte) 7); return null; }).when(random).nextBytes(any(byte[].class));
        var properties = new SecurityProperties("a".repeat(64),"issuer","aud",Duration.ofMinutes(15),Duration.ofDays(30),List.of(),false,"","","");
        service = new RefreshTokenService(repository, properties, random, Clock.fixed(now, ZoneOffset.UTC));
        user = new UserEntity("Admin","admin@example.com","hash",UserRole.ADMIN);
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }
    @Test void issuesOpaqueHashedTokenWithExpiryAndTruncatedAgent() {
        var issued = service.issue(user, UUID.randomUUID(), "127.0.0.1", "x".repeat(600));
        assertThat(issued.rawToken()).doesNotContain(service.hash(issued.rawToken()));
        assertThat(issued.entity().getExpiresAt()).isEqualTo(now.plus(Duration.ofDays(30)));
        verify(repository).save(issued.entity());
    }
    @Test void rotatesActiveToken() {
        var family = UUID.randomUUID(); var existing = new RefreshTokenEntity(user,service.hash("old"),family,now.plusSeconds(60),null,null);
        when(repository.findByHashForUpdate(service.hash("old"))).thenReturn(Optional.of(existing));
        var replacement = service.rotate("old",null,null);
        assertThat(existing.isRevoked()).isTrue(); assertThat(existing.getReplacedBy()).isSameAs(replacement.entity());
    }
    @Test void rejectsAndRevokesFamilyForExpiredToken() {
        var token = new RefreshTokenEntity(user,service.hash("old"),UUID.randomUUID(),now,null,null);
        when(repository.findByHashForUpdate(anyString())).thenReturn(Optional.of(token));
        assertThatThrownBy(() -> service.rotate("old",null,null)).isInstanceOf(AuthenticationException.class);
        verify(repository).revokeFamily(token.getTokenFamily(), now);
    }
    @Test void rejectsRevokedTokenReplayAndRevokesFamily() {
        var token = new RefreshTokenEntity(user,service.hash("old"),UUID.randomUUID(),now.plusSeconds(60),null,null); token.revoke(now.minusSeconds(1),null);
        when(repository.findByHashForUpdate(anyString())).thenReturn(Optional.of(token));
        assertThatThrownBy(() -> service.rotate("old",null,null)).isInstanceOf(AuthenticationException.class);
        verify(repository).revokeFamily(token.getTokenFamily(), now);
    }
    @Test void rejectsDisabledUser() {
        user.setEnabled(false); var token = new RefreshTokenEntity(user,service.hash("old"),UUID.randomUUID(),now.plusSeconds(60),null,null);
        when(repository.findByHashForUpdate(anyString())).thenReturn(Optional.of(token));
        assertThatThrownBy(() -> service.rotate("old",null,null)).isInstanceOf(AuthenticationException.class);
    }
    @Test void revokeIsIdempotentAndMissingTokenIsAccepted() {
        var token = new RefreshTokenEntity(user,service.hash("old"),UUID.randomUUID(),now.plusSeconds(60),null,null);
        when(repository.findByHashForUpdate(service.hash("old"))).thenReturn(Optional.of(token)); service.revoke("old"); service.revoke("old"); service.revoke("missing");
        assertThat(token.isRevoked()).isTrue(); verify(repository, times(1)).save(token);
    }
}

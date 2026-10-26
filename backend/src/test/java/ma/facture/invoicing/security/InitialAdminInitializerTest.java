package ma.facture.invoicing.security;

import ma.facture.invoicing.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.env.Environment;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InitialAdminInitializerTest {
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordService passwords = mock(PasswordService.class);
    private final Environment environment = mock(Environment.class);

    @Test void failsClearlyWhenProductionConfigurationIsMissing() {
        when(environment.matchesProfiles("prod")).thenReturn(true);
        var initializer = initializer("", "", "");
        assertThatThrownBy(() -> initializer.run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("required in production");
    }

    @Test void permitsMissingBootstrapConfigurationOutsideProduction() {
        initializer("", "", "").run(mock(ApplicationArguments.class));
        verifyNoInteractions(users, passwords);
    }

    @Test void neverOverwritesAnExistingAccount() {
        when(users.existsByEmailIgnoreCase("admin@example.com")).thenReturn(true);
        initializer("Admin", " ADMIN@example.com ", "StrongPassword123!").run(mock(ApplicationArguments.class));
        verify(passwords, never()).hash(any()); verify(users, never()).save(any());
    }

    @Test void rejectsAWeakInitialAdministratorPassword() {
        var initializer = initializer("Admin", "admin@example.com", "weak-password");

        assertThatThrownBy(() -> initializer.run(mock(ApplicationArguments.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("12 to 128 characters");
        verifyNoInteractions(users, passwords);
    }

    @Test void hashesAndCreatesTheInitialAdministratorOnce() {
        when(passwords.hash("StrongPassword123!")).thenReturn("bcrypt-hash");
        when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        initializer(" Administrator ", " ADMIN@example.com ", "StrongPassword123!").run(mock(ApplicationArguments.class));
        verify(passwords).hash("StrongPassword123!"); verify(users).save(argThat(user ->
                user.getFullName().equals("Administrator") && user.getEmail().equals("admin@example.com")
                        && user.getPasswordHash().equals("bcrypt-hash")));
    }

    private InitialAdminInitializer initializer(String name, String email, String password) {
        var properties = new SecurityProperties("a".repeat(64), "issuer", "audience",
                Duration.ofMinutes(15), Duration.ofDays(30), List.of(), false, name, email, password);
        return new InitialAdminInitializer(properties, users, passwords, environment);
    }
}

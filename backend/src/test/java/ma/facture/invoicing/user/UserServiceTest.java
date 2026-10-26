package ma.facture.invoicing.user;

import ma.facture.invoicing.common.ConflictException;
import ma.facture.invoicing.security.PasswordService;
import org.junit.jupiter.api.*;
import org.mockito.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserServiceTest {
    @Mock UserRepository repository; @Mock PasswordService passwords; @InjectMocks UserService service;
    @BeforeEach void setup(){ MockitoAnnotations.openMocks(this); }
    @Test void normalizesEmail() { assertThat(UserService.normalizeEmail(" Admin@Example.COM ")).isEqualTo("admin@example.com"); }
    @Test void preventsDisablingFinalAdmin() {
        var admin = new UserEntity("Admin","admin@example.com","hash",UserRole.ADMIN);
        when(repository.findById(admin.getId())).thenReturn(Optional.of(admin)); when(repository.countByRoleAndEnabledTrue(UserRole.ADMIN)).thenReturn(1L);
        assertThatThrownBy(() -> service.setStatus(admin.getId(), false)).isInstanceOf(ConflictException.class);
    }
    @Test void allowsDisablingWhenAnotherAdminExists() {
        var admin = new UserEntity("Admin","admin@example.com","hash",UserRole.ADMIN);
        when(repository.findById(admin.getId())).thenReturn(Optional.of(admin)); when(repository.countByRoleAndEnabledTrue(UserRole.ADMIN)).thenReturn(2L);
        assertThat(service.setStatus(admin.getId(), false).enabled()).isFalse();
    }
    @Test void updatesUserWithoutChangingPasswordWhenPasswordIsAbsent() {
        var user = new UserEntity("Old", "old@example.com", "existing-hash", UserRole.VIEWER);
        when(repository.findById(user.getId())).thenReturn(Optional.of(user));
        var response = service.update(user.getId(), new UserDtos.UpdateRequest(" New Name ", " NEW@example.com ", null, UserRole.INVOICE_MANAGER));
        assertThat(response.fullName()).isEqualTo("New Name"); assertThat(response.email()).isEqualTo("new@example.com");
        verify(passwords, never()).hash(anyString());
    }
    @Test void updatesPasswordAndRejectsDuplicateEmail() {
        var user = new UserEntity("Old", "old@example.com", "existing-hash", UserRole.VIEWER);
        when(repository.findById(user.getId())).thenReturn(Optional.of(user));
        when(repository.existsByEmailIgnoreCaseAndIdNot("taken@example.com", user.getId())).thenReturn(true);
        assertThatThrownBy(() -> service.update(user.getId(), new UserDtos.UpdateRequest("Name", "taken@example.com", "StrongPassword123!", UserRole.VIEWER))).isInstanceOf(ConflictException.class);
        when(repository.existsByEmailIgnoreCaseAndIdNot("new@example.com", user.getId())).thenReturn(false);
        when(passwords.hash("StrongPassword123!")).thenReturn("new-hash");
        service.update(user.getId(), new UserDtos.UpdateRequest("Name", "new@example.com", "StrongPassword123!", UserRole.VIEWER));
        verify(passwords).hash("StrongPassword123!");
    }
}

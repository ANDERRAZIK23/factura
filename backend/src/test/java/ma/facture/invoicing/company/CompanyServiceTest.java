package ma.facture.invoicing.company;

import ma.facture.invoicing.common.ConflictException;
import ma.facture.invoicing.common.NotFoundException;
import ma.facture.invoicing.security.AuthenticatedUser;
import ma.facture.invoicing.security.PasswordService;
import ma.facture.invoicing.user.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CompanyServiceTest {
    private final CompanyRepository companies = mock(CompanyRepository.class);
    private final CompanyCodeGenerator codes = mock(CompanyCodeGenerator.class);
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordService passwords = mock(PasswordService.class);
    private final CompanyService service = new CompanyService(companies, codes, users, passwords);

    @BeforeEach void saveReturnsArgument() {
        when(companies.saveAndFlush(any(Company.class))).thenAnswer(call -> call.getArgument(0));
        when(users.save(any(UserEntity.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test void createsCompanyAndItsFirstTenantAdministratorAtomically() {
        when(codes.generate()).thenReturn("ENT-000002");
        when(passwords.hash("StrongPassword123!")).thenReturn("encoded-password");

        CompanyDtos.Response response = service.create(new CompanyDtos.CreateRequest(
                "  Atlas SARL  ", "  Casablanca  ", "  +212500000000  ", "  BILLING@ATLAS.MA  ",
                "001527304000014", "  Atlas Admin  ", "  ADMIN@ATLAS.MA  ", "StrongPassword123!"));

        assertThat(response.companyCode()).isEqualTo("ENT-000002");
        assertThat(response.companyName()).isEqualTo("Atlas SARL");
        assertThat(response.email()).isEqualTo("billing@atlas.ma");
        ArgumentCaptor<UserEntity> admin = ArgumentCaptor.forClass(UserEntity.class);
        verify(users).save(admin.capture());
        assertThat(admin.getValue().getCompanyId()).isEqualTo(response.id());
        assertThat(admin.getValue().getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(admin.getValue().getEmail()).isEqualTo("admin@atlas.ma");
        assertThat(admin.getValue().getPasswordHash()).isEqualTo("encoded-password");
    }

    @Test void rejectsDuplicateAdministratorEmailBeforeAllocatingCompanyCode() {
        when(users.existsByEmailIgnoreCase("admin@atlas.ma")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CompanyDtos.CreateRequest(
                "Atlas", null, null, null, null, "Admin", "ADMIN@ATLAS.MA", "StrongPassword123!")))
                .isInstanceOf(ConflictException.class).hasMessageContaining("e-mail");
        verifyNoInteractions(codes, passwords);
        verify(companies, never()).saveAndFlush(any());
    }

    @Test void preventsApplicationAdministratorFromDisablingTheirOwnCompany() {
        Company company = new Company("ENT-000002", "Atlas", null, null, null, null);
        when(companies.findById(company.getId())).thenReturn(Optional.of(company));
        AuthenticatedUser actor = new AuthenticatedUser(UUID.randomUUID(), "platform@example.com", UserRole.APP_ADMIN,
                company.getId());

        assertThatThrownBy(() -> service.setStatus(company.getId(), new CompanyDtos.StatusRequest(false, 0), actor))
                .isInstanceOf(ConflictException.class).hasMessageContaining("ne peut pas être désactivée");
        verify(companies, never()).saveAndFlush(any());
    }

    @Test void platformCreatesAUserForTheSelectedCompanyOnly() {
        Company company = new Company("ENT-000002", "Atlas", null, null, null, null);
        when(companies.findById(company.getId())).thenReturn(Optional.of(company));
        when(passwords.hash("StrongPassword123!")).thenReturn("encoded-password");

        UserDtos.Response response = service.createUser(company.getId(), new UserDtos.CreateRequest(
                "  Atlas Viewer  ", "  VIEWER@ATLAS.MA  ", "StrongPassword123!", UserRole.VIEWER));

        assertThat(response.companyId()).isEqualTo(company.getId());
        assertThat(response.email()).isEqualTo("viewer@atlas.ma");
        assertThat(response.role()).isEqualTo(UserRole.VIEWER);
        ArgumentCaptor<UserEntity> saved = ArgumentCaptor.forClass(UserEntity.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getCompanyId()).isEqualTo(company.getId());
    }

    @Test void platformCannotCreateAUserForAnUnknownCompanyOrProvisionAnotherAppAdmin() {
        UUID missing = UUID.randomUUID();
        assertThatThrownBy(() -> service.createUser(missing, new UserDtos.CreateRequest(
                "User", "user@example.com", "StrongPassword123!", UserRole.VIEWER)))
                .isInstanceOf(NotFoundException.class);

        Company company = new Company("ENT-000002", "Atlas", null, null, null, null);
        when(companies.findById(company.getId())).thenReturn(Optional.of(company));
        assertThatThrownBy(() -> service.createUser(company.getId(), new UserDtos.CreateRequest(
                "Platform", "platform@example.com", "StrongPassword123!", UserRole.APP_ADMIN)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("administrateur plateforme");
    }
}

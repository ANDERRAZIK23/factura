package ma.facture.invoicing.client.service;

import ma.facture.invoicing.client.dto.*;
import ma.facture.invoicing.client.entity.Client;
import ma.facture.invoicing.client.exception.*;
import ma.facture.invoicing.client.mapper.ClientMapper;
import ma.facture.invoicing.client.repository.ClientRepository;
import ma.facture.invoicing.security.AuthenticatedUser;
import ma.facture.invoicing.user.UserRole;
import org.junit.jupiter.api.*;
import org.springframework.data.domain.*;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ClientServiceTest {
    private final ClientRepository repository = mock(ClientRepository.class);
    private final ClientCodeGenerator codes = mock(ClientCodeGenerator.class);
    private final ClientMapper mapper = new ClientMapper();
    private final ClientService service = new ClientService(repository, codes, mapper);
    private final AuthenticatedUser actor = new AuthenticatedUser(UUID.randomUUID(), "admin@example.com", UserRole.ADMIN);

    @BeforeEach void saveReturnsArgument() {
        when(repository.saveAndFlush(any(Client.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test void createsAndNormalizesClientWithAuditData() {
        when(codes.generate()).thenReturn("CLI-000001");
        ClientResponse response = service.create(new ClientCreateRequest("  Acme SARL  ", " +212   5-00 ", " Casa ", " SALES@EXAMPLE.COM ", "001 527 304 000 014"), actor);
        assertThat(response.clientCode()).isEqualTo("CLI-000001");
        assertThat(response.clientName()).isEqualTo("Acme SARL");
        assertThat(response.phoneNumber()).isEqualTo("+212 5-00");
        assertThat(response.address()).isEqualTo("Casa");
        assertThat(response.email()).isEqualTo("sales@example.com");
        assertThat(response.ice()).isEqualTo("001527304000014");
        assertThat(response.createdBy()).isEqualTo(actor.id());
        assertThat(response.updatedBy()).isEqualTo(actor.id());
        assertThat(response.active()).isTrue();
    }

    @Test void rejectsDuplicateIceBeforeGeneratingCode() {
        when(repository.existsByCompanyIdAndIce(actor.companyId(), "001527304000014")).thenReturn(true);
        assertThatThrownBy(() -> service.create(request("001527304000014"), actor)).isInstanceOf(DuplicateClientIceException.class);
        verifyNoInteractions(codes);
    }

    @Test void mapsDatabaseIceRaceToDuplicateConflictOnCreateAndUpdate() {
        when(codes.generate()).thenReturn("CLI-000010");
        when(repository.saveAndFlush(any(Client.class))).thenThrow(new DataIntegrityViolationException("uq_clients_company_ice_not_null"));
        assertThatThrownBy(() -> service.create(request("001527304000014"), actor)).isInstanceOf(DuplicateClientIceException.class);

        reset(repository);
        Client client = new Client("CLI-000011", "Name", null, null, null, null, actor.id());
        when(repository.findByIdAndCompanyId(client.getId(), actor.companyId())).thenReturn(Optional.of(client));
        when(repository.saveAndFlush(client)).thenThrow(new DataIntegrityViolationException("uq_clients_company_ice_not_null"));
        assertThatThrownBy(() -> service.update(client.getId(), new ClientUpdateRequest("Name", null, null, null, "001527304000014", 0), actor))
                .isInstanceOf(DuplicateClientIceException.class);
    }

    @Test void retriesClientCodeConflictsAndStopsAtTheControlledLimit() {
        when(codes.generate()).thenReturn("CLI-000020", "CLI-000021", "CLI-000022");
        when(repository.saveAndFlush(any(Client.class)))
                .thenThrow(new DataIntegrityViolationException("clients_client_code_key"))
                .thenReturn(new Client("CLI-000021", "Acme", null, null, null, null, actor.id()));
        assertThat(service.create(request(null), actor).clientCode()).isEqualTo("CLI-000021");

        reset(repository, codes);
        when(codes.generate()).thenReturn("CLI-000030", "CLI-000031", "CLI-000032");
        when(repository.saveAndFlush(any(Client.class))).thenThrow(new DataIntegrityViolationException("clients_client_code_key"));
        assertThatThrownBy(() -> service.create(request(null), actor)).isInstanceOf(DataIntegrityViolationException.class);
        verify(repository, times(3)).saveAndFlush(any(Client.class));
    }

    @Test void doesNotRetryUnknownIntegrityConflicts() {
        when(codes.generate()).thenReturn("CLI-000040");
        when(repository.saveAndFlush(any(Client.class))).thenThrow(new DataIntegrityViolationException(null, new IllegalStateException("other_constraint")));
        assertThatThrownBy(() -> service.create(request(null), actor)).isInstanceOf(DataIntegrityViolationException.class);
        verify(repository).saveAndFlush(any(Client.class));

        reset(repository);
        Client client = new Client("CLI-000041", "Name", null, null, null, null, actor.id());
        when(repository.findByIdAndCompanyId(client.getId(), actor.companyId())).thenReturn(Optional.of(client));
        when(repository.saveAndFlush(client)).thenThrow(new DataIntegrityViolationException("other_constraint"));
        assertThatThrownBy(() -> service.update(client.getId(), new ClientUpdateRequest("Name", null, null, null, null, 0), actor))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void updatesEditableFieldsButNotCodeAndSetsUpdater() {
        UUID originalActor = UUID.randomUUID();
        Client client = new Client("CLI-000111", "Old Name", null, null, null, null, originalActor);
        when(repository.findByIdAndCompanyId(client.getId(), actor.companyId())).thenReturn(Optional.of(client));
        ClientResponse response = service.update(client.getId(), new ClientUpdateRequest(" New Name ", null, null, "NEW@EXAMPLE.COM", "", 0), actor);
        assertThat(response.clientCode()).isEqualTo("CLI-000111");
        assertThat(response.clientName()).isEqualTo("New Name");
        assertThat(response.email()).isEqualTo("new@example.com");
        assertThat(response.ice()).isNull();
        assertThat(response.createdBy()).isEqualTo(originalActor);
        assertThat(response.updatedBy()).isEqualTo(actor.id());
    }

    @Test void rejectsStaleUpdateAndDuplicateIce() {
        Client client = new Client("CLI-000112", "Name", null, null, null, null, actor.id());
        when(repository.findByIdAndCompanyId(client.getId(), actor.companyId())).thenReturn(Optional.of(client));
        assertThatThrownBy(() -> service.update(client.getId(), new ClientUpdateRequest("Name", null, null, null, null, 1), actor))
                .isInstanceOf(ClientConcurrencyException.class);
        when(repository.existsByCompanyIdAndIceAndIdNot(actor.companyId(), "001527304000014", client.getId())).thenReturn(true);
        assertThatThrownBy(() -> service.update(client.getId(), new ClientUpdateRequest("Name", null, null, null, "001527304000014", 0), actor))
                .isInstanceOf(DuplicateClientIceException.class);
    }

    @Test void changesStatusAndTreatsRepeatedStatusAsIdempotent() {
        Client client = new Client("CLI-000113", "Name", null, null, null, null, actor.id());
        when(repository.findByIdAndCompanyId(client.getId(), actor.companyId())).thenReturn(Optional.of(client));
        assertThat(service.updateStatus(client.getId(), new ClientStatusRequest(false, 0), actor).active()).isFalse();
        clearInvocations(repository);
        assertThat(service.updateStatus(client.getId(), new ClientStatusRequest(false, 99), actor).active()).isFalse();
        verify(repository, never()).saveAndFlush(any());
    }

    @Test void rejectsStaleStatusAndUnknownClient() {
        Client client = new Client("CLI-000114", "Name", null, null, null, null, actor.id());
        when(repository.findByIdAndCompanyId(client.getId(), actor.companyId())).thenReturn(Optional.of(client));
        assertThatThrownBy(() -> service.updateStatus(client.getId(), new ClientStatusRequest(false, 2), actor)).isInstanceOf(ClientConcurrencyException.class);
        UUID missing = UUID.randomUUID();
        when(repository.findByIdAndCompanyId(missing, actor.companyId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(missing)).isInstanceOf(ClientNotFoundException.class);
    }

    @Test void normalizesSearchAndEnforcesPagingAndSortAllowlist() {
        assertThat(ClientService.normalizeSearch("  ACME  ")).isEqualTo("acme");
        assertThat(ClientService.normalizeSearch("   ")).isNull();
        assertThat(ClientService.normalizeSearch(null)).isNull();
        assertThat(ClientService.parseSort(null)).isEqualTo(Sort.by(Sort.Direction.ASC, "clientName"));
        assertThat(ClientService.parseSort(" ")).isEqualTo(Sort.by(Sort.Direction.ASC, "clientName"));
        assertThat(ClientService.parseSort("createdAt,desc")).isEqualTo(Sort.by(Sort.Direction.DESC, "createdAt"));
        assertThatThrownBy(() -> ClientService.parseSort("clientName")).isInstanceOf(InvalidClientQueryException.class);
        assertThatThrownBy(() -> ClientService.parseSort("ice,asc")).isInstanceOf(InvalidClientQueryException.class);
        assertThatThrownBy(() -> ClientService.parseSort("clientName,sideways")).isInstanceOf(InvalidClientQueryException.class);
        assertThatThrownBy(() -> service.list(0, 101, null, null, null)).isInstanceOf(InvalidClientQueryException.class);
        assertThatThrownBy(() -> service.list(0, 0, null, null, null)).isInstanceOf(InvalidClientQueryException.class);
        assertThatThrownBy(() -> service.list(-1, 20, null, null, null)).isInstanceOf(InvalidClientQueryException.class);
    }

    @Test void listsOnlyTheRequestedPage() {
        Client client = new Client("CLI-000115", "Acme", null, null, null, null, actor.id());
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(client), PageRequest.of(1, 2), 3));
        ClientPageResponse page = service.list(1, 2, "clientCode,asc", " Acme ", true);
        assertThat(page.content()).extracting(ClientSummaryResponse::clientCode).containsExactly("CLI-000115");
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.totalElements()).isEqualTo(3);
    }

    private static ClientCreateRequest request(String ice) { return new ClientCreateRequest("Acme", null, null, null, ice); }
}

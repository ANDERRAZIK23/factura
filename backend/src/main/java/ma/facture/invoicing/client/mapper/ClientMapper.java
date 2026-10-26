package ma.facture.invoicing.client.mapper;

import ma.facture.invoicing.client.dto.*;
import ma.facture.invoicing.client.entity.Client;
import org.springframework.stereotype.Component;

@Component
public class ClientMapper {
    public ClientResponse toResponse(Client client) {
        return new ClientResponse(client.getId(), client.getClientCode(), client.getClientName(),
                client.getPhoneNumber(), client.getAddress(), client.getEmail(), client.getIce(),
                client.isActive(), client.getCreatedAt(), client.getUpdatedAt(), client.getCreatedBy(),
                client.getUpdatedBy(), client.getVersion());
    }

    public ClientSummaryResponse toSummary(Client client) {
        return new ClientSummaryResponse(client.getId(), client.getClientCode(), client.getClientName(),
                client.getPhoneNumber(), client.getEmail(), client.getIce(), client.isActive(),
                client.getCreatedAt(), client.getUpdatedAt(), client.getVersion());
    }
}

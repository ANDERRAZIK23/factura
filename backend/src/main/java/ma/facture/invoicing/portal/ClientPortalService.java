package ma.facture.invoicing.portal;
import ma.facture.invoicing.client.repository.ClientRepository;
import ma.facture.invoicing.company.CompanyRepository;
import ma.facture.invoicing.common.*;
import ma.facture.invoicing.invoice.entity.*;
import ma.facture.invoicing.invoice.pdf.*;
import ma.facture.invoicing.invoice.repository.InvoiceRepository;
import ma.facture.invoicing.invoice.settlement.*;
import ma.facture.invoicing.security.AuthenticatedUser;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service
public class ClientPortalService {
    private final ClientPortalAccessRepository accesses;
    private final ClientRepository clients;
    private final CompanyRepository companies;
    private final InvoiceRepository invoices;
    private final InvoicePaymentRepository payments;
    private final InvoicePdfService pdf;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    public ClientPortalService(ClientPortalAccessRepository accesses, ClientRepository clients, CompanyRepository companies,
            InvoiceRepository invoices, InvoicePaymentRepository payments, InvoicePdfService pdf, JdbcTemplate jdbc, Clock clock) {
        this.accesses = accesses; this.clients = clients; this.companies = companies; this.invoices = invoices;
        this.payments = payments; this.pdf = pdf; this.jdbc = jdbc; this.clock = clock;
    }
    public record AccessInfo(Instant expiresAt, boolean active) {}
    public record NewAccess(String token, Instant expiresAt) {}
    public record PublicInvoice(UUID id, String invoiceNumber, LocalDate invoiceDate, LocalDate dueDate,
            InvoiceStatus status, BigDecimal totalIncludingTax, BigDecimal paidAmount, BigDecimal outstandingAmount, boolean pdfAvailable) {
        static PublicInvoice from(Invoice i) { return new PublicInvoice(i.getId(), i.getInvoiceNumber(), i.getInvoiceDate(), i.getDueDate(), i.getStatus(),
                i.getTotalIncludingTax(), i.getPaidAmount(), i.getOutstandingAmount(), i.getPdfFilePath() != null); }
    }
    public record PortalPage(String companyName, String clientName, List<PublicInvoice> invoices, int page, boolean hasNext, BigDecimal totalOutstanding) {}
    public record PublicPayment(BigDecimal amount, LocalDate paymentDate, String paymentMethod, Instant reversedAt) {}
    @Transactional(readOnly = true)
    public AccessInfo info(UUID client, AuthenticatedUser actor) {
        clients.findByIdAndCompanyId(client, actor.companyId()).orElseThrow(() -> new NotFoundException("Client introuvable"));
        return accesses.findByClientIdAndRevokedAtIsNull(client).map(a -> new AccessInfo(a.getExpiresAt(), a.getExpiresAt().isAfter(Instant.now(clock)))).orElse(new AccessInfo(null, false));
    }
    @Transactional
    public NewAccess create(UUID client, int days, AuthenticatedUser actor) {
        var customer = clients.findByIdAndCompanyIdForUpdate(client, actor.companyId()).orElseThrow(() -> new NotFoundException("Client introuvable"));
        if (!customer.isActive()) throw new ConflictException("Réactivez le client avant de créer son accès");
        if (days < 1 || days > 90) throw new ValidationException("La durée du lien doit être comprise entre 1 et 90 jours");
        revokeActive(client);
        byte[] secret = new byte[32]; random.nextBytes(secret);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        Instant now = Instant.now(clock), expiry = now.plus(Duration.ofDays(days));
        accesses.saveAndFlush(new ClientPortalAccess(actor.companyId(), client, hash(token), expiry, actor.id(), now));
        return new NewAccess(token, expiry);
    }
    @Transactional
    public void revoke(UUID client, AuthenticatedUser actor) {
        clients.findByIdAndCompanyIdForUpdate(client, actor.companyId()).orElseThrow(() -> new NotFoundException("Client introuvable"));
        revokeActive(client);
    }
    private void revokeActive(UUID client) {
        accesses.findByClientIdAndRevokedAtIsNull(client).ifPresent(a -> { a.revoke(Instant.now(clock)); accesses.saveAndFlush(a); });
    }
    @Transactional(readOnly = true)
    public PortalPage list(String authorization, int page) {
        var access = authenticate(authorization);
        if (page < 0) throw new ValidationException("Page invalide");
        var company = companies.findById(access.getCompanyId()).orElseThrow(() -> new AuthenticationException("Accès indisponible"));
        var client = clients.findByIdAndCompanyId(access.getClientId(), access.getCompanyId()).orElseThrow(() -> new AuthenticationException("Accès indisponible"));
        var data = invoices.findAll((root, query, cb) -> cb.and(cb.equal(root.get("companyId"), access.getCompanyId()),
                cb.equal(root.get("client").get("id"), access.getClientId()), cb.isNotNull(root.get("invoiceNumber"))),
                PageRequest.of(page, 20, Sort.by("invoiceDate").descending().and(Sort.by("id"))));
        BigDecimal outstanding = jdbc.queryForObject("select coalesce(sum(total_including_tax - paid_amount), 0) from invoices where company_id = ? and client_id = ? and status = 'VALIDATED'", BigDecimal.class, access.getCompanyId(), access.getClientId());
        return new PortalPage(company.getCompanyName(), client.getClientName(), data.stream().map(PublicInvoice::from).toList(), page, data.hasNext(), outstanding);
    }
    @Transactional(readOnly = true)
    public List<PublicPayment> payments(String authorization, UUID id) {
        ownedInvoice(authorization, id);
        return payments.findAllByInvoiceIdOrderByCreatedAtDesc(id).stream().map(p -> new PublicPayment(p.getAmount(), p.getPaymentDate(), p.getPaymentMethod().name(), p.getReversedAt())).toList();
    }
    @Transactional(readOnly = true)
    public InvoicePdfDownload pdf(String authorization, UUID id) { return pdf.download(ownedInvoice(authorization, id)); }
    private Invoice ownedInvoice(String authorization, UUID id) {
        var access = authenticate(authorization);
        return invoices.findDetailedByIdAndCompanyId(id, access.getCompanyId())
                .filter(i -> i.getClient().getId().equals(access.getClientId()) && i.getInvoiceNumber() != null)
                .orElseThrow(() -> new NotFoundException("Facture introuvable"));
    }
    private ClientPortalAccess authenticate(String authorization) {
        if (authorization == null || !authorization.matches("Portal [A-Za-z0-9_-]{43}")) throw new AuthenticationException("Lien client invalide ou expiré");
        var access = accesses.findByTokenHash(hash(authorization.substring(7)))
                .filter(a -> a.getRevokedAt() == null && a.getExpiresAt().isAfter(Instant.now(clock)))
                .orElseThrow(() -> new AuthenticationException("Lien client invalide ou expiré"));
        if (companies.findById(access.getCompanyId()).filter(c -> c.isEnabled()).isEmpty()
                || clients.findByIdAndCompanyId(access.getClientId(), access.getCompanyId()).filter(c -> c.isActive()).isEmpty())
            throw new AuthenticationException("Cet accès client n’est plus disponible");
        return access;
    }
    private static String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 indisponible", exception); }
    }
}

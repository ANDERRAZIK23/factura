package ma.facture.invoicing.integration;

import com.fasterxml.jackson.databind.*;
import ma.facture.invoicing.client.entity.Client;
import ma.facture.invoicing.client.repository.ClientRepository;
import ma.facture.invoicing.company.*;
import ma.facture.invoicing.product.entity.*;
import ma.facture.invoicing.product.repository.ProductRepository;
import ma.facture.invoicing.sales.*;
import ma.facture.invoicing.security.JwtService;
import ma.facture.invoicing.user.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers
@Import(SalesWorkflowIT.TimeConfig.class)
class SalesWorkflowIT {
    @Container static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");
    @TempDir static Path storage;
    static final AtomicInteger COMPANY_SEQUENCE = new AtomicInteger(980000);
    static class TestClock extends Clock {
        private Instant now = Instant.parse("2026-08-04T10:15:30Z");
        void set(String date) { now = Instant.parse(date + "T10:15:30Z"); }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        public Instant instant() { return now; }
    }
    @TestConfiguration static class TimeConfig { @Bean @Primary TestClock testClock() { return new TestClock(); } }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl); r.add("spring.datasource.username", POSTGRES::getUsername); r.add("spring.datasource.password", POSTGRES::getPassword);
        r.add("app.security.jwt-secret", () -> "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        r.add("app.contracts.expiration-job.enabled", () -> "false"); r.add("app.invoices.recurring-job.enabled", () -> "false");
        r.add("app.invoice.storage.path", () -> storage.toString());
    }
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users; @Autowired ClientRepository clients; @Autowired ProductRepository products;
    @Autowired CompanyRepository companies; @Autowired JwtService jwt; @Autowired TestClock clock;
    @Autowired RecurringInvoiceService recurring;
    private UserEntity admin, other; private Client client, secondClient; private Product product;
    private String token, otherToken, viewerToken;
    @BeforeEach void seed() {
        jdbc.execute("TRUNCATE users, clients, products, invoices, quotations, recurring_invoices RESTART IDENTITY CASCADE");
        clock.set("2026-08-04");
        admin = users.save(new UserEntity("Samira", "samira@example.com", "unused", UserRole.ADMIN));
        var company = companies.save(new Company("ENT-" + COMPANY_SEQUENCE.incrementAndGet(), "Other Company", null, null, null, null));
        other = users.save(new UserEntity("Other", "other@example.com", "unused", UserRole.ADMIN, company.getId()));
        var viewer = users.save(new UserEntity("Viewer", "viewer@example.com", "unused", UserRole.VIEWER));
        token = jwt.createAccessToken(admin); otherToken = jwt.createAccessToken(other); viewerToken = jwt.createAccessToken(viewer);
        client = clients.save(new Client("CLI-990001", "Atlas Client", "0500000000", "Casablanca", "client@example.com", "001527304000014", admin.getId()));
        secondClient = clients.save(new Client("CLI-990002", "Second Client", null, null, null, null, admin.getId()));
        product = products.save(new Product("PRD-990001", "Article original", "Description", new BigDecimal("100.00"), new BigDecimal("20.00"), ProductUnit.PIECE, admin.getId()));
    }
    private Map<String,Object> document(UUID clientId) {
        return Map.of("invoiceDate","2026-08-01","clientId",clientId,"paymentMethod","BANK_TRANSFER",
                "customerOrderReference","CMD-42","lines",List.of(Map.of("productId",product.getId(),"quantity",2,"unitPriceExcludingTax",100,"vatRate",20,"discountRate",10)));
    }
    private JsonNode request(MockHttpServletRequestBuilder request, String bearer, Object data, int expected) throws Exception {
        if (bearer != null) request.header(HttpHeaders.AUTHORIZATION, bearer.startsWith("Portal ") ? bearer : "Bearer " + bearer);
        if (data != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(data));
        var response = mvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).withFailMessage(response.getContentAsString()).isEqualTo(expected);
        return response.getContentAsByteArray().length == 0 ? json.nullNode() : json.readTree(response.getContentAsByteArray());
    }
    private JsonNode invoice(UUID clientId) throws Exception {
        var draft = request(post("/api/v1/invoices"), token, document(clientId), 201);
        return request(post("/api/v1/invoices/" + draft.path("id").asText() + "/validate"), token, Map.of("version", draft.path("version").asLong()), 200);
    }
    private JsonNode quote() throws Exception { return request(post("/api/v1/quotations"), token, Map.of("document",document(client.getId()),"validUntil","2026-09-01"), 201); }
    private JsonNode pay(JsonNode invoice, String amount, int expected) throws Exception {
        return request(post("/api/v1/invoices/" + invoice.path("id").asText() + "/payments"), token,
                Map.of("version",invoice.path("version").asLong(),"amount",new BigDecimal(amount),"paymentDate","2026-08-03","paymentMethod","BANK_TRANSFER","reference","VIR-42"), expected);
    }
    private void process(UUID id) {
        long version = jdbc.queryForObject("select version from recurring_invoices where id=?", Long.class, id);
        recurring.process(id, version);
    }
    @Test void quoteConvertsOnceWithoutRepricingAndKeepsItsSnapshot() throws Exception {
        var quote = quote(); String id = quote.path("id").asText();
        product.update("Article renommé", "Changed", new BigDecimal("500"), new BigDecimal("7"), ProductUnit.PIECE, admin.getId()); products.saveAndFlush(product);
        var invoice = request(post("/api/v1/quotations/" + id + "/convert"), token, Map.of("version",quote.path("version").asLong()), 200);
        assertThat(invoice.path("status").asText()).isEqualTo("DRAFT");
        assertThat(invoice.path("totalIncludingTax").decimalValue()).isEqualByComparingTo("216");
        assertThat(invoice.path("customerOrderReference").asText()).isEqualTo("CMD-42");
        var repeated = request(post("/api/v1/quotations/" + id + "/convert"), token, Map.of("version",0), 200);
        assertThat(repeated.path("id").asText()).isEqualTo(invoice.path("id").asText());
        assertThat(jdbc.queryForObject("select count(*) from invoices", Integer.class)).isEqualTo(1);
        assertThat(request(get("/api/v1/quotations/" + id), token, null, 200).path("terms").path("lines").get(0).path("productName").asText()).isEqualTo("Article original");
        request(get("/api/v1/quotations/" + id), otherToken, null, 404);
        var pdf = mvc.perform(get("/api/v1/quotations/" + id + "/pdf").header(HttpHeaders.AUTHORIZATION,"Bearer " + token)).andReturn().getResponse();
        assertThat(pdf.getStatus()).isEqualTo(200); assertThat(new String(pdf.getContentAsByteArray(), java.nio.charset.StandardCharsets.ISO_8859_1)).startsWith("%PDF-");
    }
    @Test void expiredCancelledAndForeignQuotesCannotConvert() throws Exception {
        var q = quote(); String id = q.path("id").asText();
        request(post("/api/v1/quotations/" + id + "/convert"), otherToken, Map.of("version",0), 404);
        clock.set("2026-09-02"); request(post("/api/v1/quotations/" + id + "/convert"), token, Map.of("version",0), 409);
        clock.set("2026-08-04"); request(post("/api/v1/quotations/" + id + "/cancel"), token, Map.of("version",0), 200);
        request(post("/api/v1/quotations/" + id + "/convert"), token, Map.of("version",1), 409);
    }
    @Test void partialPaymentsEnforceBalanceAndPreserveCorrectionAudit() throws Exception {
        var i = invoice(client.getId()); var partial = pay(i,"50",200);
        assertThat(partial.path("paidAmount").decimalValue()).isEqualByComparingTo("50");
        assertThat(partial.path("outstandingAmount").decimalValue()).isEqualByComparingTo("166");
        assertThat(partial.path("status").asText()).isEqualTo("VALIDATED");
        pay(partial,"167",409); pay(i,"10",409);
        var paid = pay(partial,"166",200); assertThat(paid.path("status").asText()).isEqualTo("PAID");
        var entries = request(get("/api/v1/invoices/"+i.path("id").asText()+"/payments"),token,null,200);
        var reversed = request(post("/api/v1/invoices/"+i.path("id").asText()+"/payments/"+entries.get(0).path("id").asText()+"/reverse"),token,Map.of("version",paid.path("version").asLong(),"reason","Double saisie"),200);
        assertThat(reversed.path("status").asText()).isEqualTo("VALIDATED");
        assertThat(jdbc.queryForObject("select count(*) from invoice_payments where reversed_at is not null",Integer.class)).isEqualTo(1);
        request(post("/api/v1/invoices/"+i.path("id").asText()+"/cancel"),token,Map.of("version",reversed.path("version").asLong()),409);
    }
    @Test void fullSettlementAlsoCreatesAnAuditableLedgerEntry() throws Exception {
        var i = invoice(client.getId()); var partial = pay(i,"50",200);
        var paid = request(post("/api/v1/invoices/"+i.path("id").asText()+"/mark-paid"),token,Map.of("version",partial.path("version").asLong()),200);
        assertThat(paid.path("paidAmount").decimalValue()).isEqualByComparingTo("216");
        assertThat(jdbc.queryForObject("select sum(amount) from invoice_payments", BigDecimal.class)).isEqualByComparingTo("216");
    }
    @Test void overdueUsesDueDatesAndRemindersUseTheRemainingBalance() throws Exception {
        var i = invoice(client.getId()); String path = "/api/v1/invoices/"+i.path("id").asText();
        request(post(path+"/reminders"),token,null,409);
        var dated = request(put(path+"/due-date"),token,Map.of("version",i.path("version").asLong(),"dueDate","2026-08-02"),200);
        pay(dated,"50",200);
        var list = request(get("/api/v1/collections/overdue"),token,null,200); assertThat(list.path("content").size()).isEqualTo(1);
        var reminder = request(post(path+"/reminders"),token,null,200);
        assertThat(reminder.path("body").asText()).contains("166.00 MAD", "02/08/2026");
        assertThat(reminder.path("recipient").asText()).isEqualTo("client@example.com");
        assertThat(request(get(path+"/reminders"),token,null,200).size()).isEqualTo(1);
        request(get(path+"/payments"),otherToken,null,404);
    }
    @Test void portalIsScopedReadOnlyExpiringAndRevocable() throws Exception {
        var own = invoice(client.getId()); var foreign = invoice(secondClient.getId());
        var draft = request(post("/api/v1/invoices"),token,document(client.getId()),201);
        String accessPath="/api/v1/clients/"+client.getId()+"/portal-access";
        var access=request(post(accessPath),token,Map.of("days",7),200); String secret=access.path("token").asText();
        var list=request(get("/api/v1/portal/invoices"),"Portal "+secret,null,200);
        assertThat(list.path("invoices").size()).isEqualTo(1); assertThat(list.path("invoices").get(0).path("id").asText()).isEqualTo(own.path("id").asText());
        assertThat(jdbc.queryForObject("select token_hash from client_portal_access",String.class)).doesNotContain(secret);
        request(get("/api/v1/portal/invoices/"+foreign.path("id").asText()+"/payments"),"Portal "+secret,null,404);
        request(get("/api/v1/portal/invoices/"+draft.path("id").asText()+"/payments"),"Portal "+secret,null,404);
        request(get("/api/v1/invoices"),"Portal "+secret,null,401);
        request(get("/api/v1/portal/invoices"),null,null,401);
        request(post(accessPath),otherToken,Map.of("days",7),404);
        request(delete(accessPath),token,null,204); request(get("/api/v1/portal/invoices"),"Portal "+secret,null,401);
        access=request(post(accessPath),token,Map.of("days",1),200); clock.set("2026-08-06");
        request(get("/api/v1/portal/invoices"),"Portal "+access.path("token").asText(),null,401);
    }
    @Test void recurringPreparationIsIdempotentAndDoesNotDriftAfterFebruary() throws Exception {
        var i = invoice(client.getId());
        var schedule=request(post("/api/v1/recurring-invoices"),token,Map.of("invoiceId",i.path("id").asText(),"title","Mensuel","startDate","2026-01-31","endDate","2026-03-31","intervalMonths",1,"paymentTermsDays",30),200);
        String path="/api/v1/recurring-invoices/"+schedule.path("id").asText();
        var generated=request(post(path+"/generate"),token,Map.of("version",0),200);
        assertThat(generated.path("status").asText()).isEqualTo("DRAFT"); assertThat(generated.path("invoiceDate").asText()).isEqualTo("2026-01-31");
        request(post(path+"/generate"),token,Map.of("version",0),409);
        process(UUID.fromString(schedule.path("id").asText()));
        var r=request(get("/api/v1/recurring-invoices"),token,null,200).path("content").get(0);
        assertThat(r.path("nextDate").asText()).isEqualTo("2026-03-31");
        process(UUID.fromString(schedule.path("id").asText()));
        r=request(get("/api/v1/recurring-invoices"),token,null,200).path("content").get(0);
        assertThat(r.path("enabled").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from recurring_invoice_runs",Integer.class)).isEqualTo(3);
    }
    @Test void viewersCannotChangeSalesOrCreatePortalLinks() throws Exception {
        request(post("/api/v1/quotations"),viewerToken,Map.of("document",document(client.getId()),"validUntil","2026-09-01"),403);
        var i=invoice(client.getId());
        request(post("/api/v1/invoices/"+i.path("id").asText()+"/payments"),viewerToken,Map.of("version",0,"amount",1,"paymentDate","2026-08-03","paymentMethod","CASH","reference",""),403);
        request(post("/api/v1/clients/"+client.getId()+"/portal-access"),viewerToken,Map.of("days",30),403);
        request(get("/api/v1/quotations"),viewerToken,null,200);
    }
    @Test void concurrentPaymentsCannotOverpayOrLeaveAnOrphanLedgerEntry() throws Exception {
        var i = invoice(client.getId());
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> attempt = () -> { start.await(); return mvc.perform(post("/api/v1/invoices/"+i.path("id").asText()+"/payments")
                    .header(HttpHeaders.AUTHORIZATION,"Bearer "+token).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsBytes(Map.of("version",i.path("version").asLong(),"amount",150,"paymentDate","2026-08-03","paymentMethod","CASH","reference","Concurrent"))))
                    .andReturn().getResponse().getStatus(); };
            var one=pool.submit(attempt); var two=pool.submit(attempt); start.countDown();
            assertThat(List.of(one.get(20,TimeUnit.SECONDS),two.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
            assertThat(jdbc.queryForObject("select paid_amount from invoices where id=?",BigDecimal.class,UUID.fromString(i.path("id").asText()))).isEqualByComparingTo("150");
            assertThat(jdbc.queryForObject("select count(*) from invoice_payments",Integer.class)).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }
    @Test void failedRecurrencesPauseAndCanBeTakenOverByAnAuthorizedManager() throws Exception {
        var i=invoice(client.getId());
        var r=request(post("/api/v1/recurring-invoices"),token,Map.of("invoiceId",i.path("id").asText(),"title","Mensuel","startDate","2026-08-01","intervalMonths",1,"paymentTermsDays",30),200);
        UUID id=UUID.fromString(r.path("id").asText());
        recurring.recordFailure(id,r.path("version").asLong(),"Article désactivé");
        var paused=request(get("/api/v1/recurring-invoices"),token,null,200).path("content").get(0);
        assertThat(paused.path("enabled").asBoolean()).isFalse(); assertThat(paused.path("lastError").asText()).isEqualTo("Article désactivé");
        var manager=users.save(new UserEntity("Manager","manager@example.com","unused",UserRole.INVOICE_MANAGER));
        String managerToken=jwt.createAccessToken(manager);
        var resumed=request(put("/api/v1/recurring-invoices/"+id+"/status"),managerToken,Map.of("version",paused.path("version").asLong(),"enabled",true),200);
        assertThat(resumed.path("executionUserId").asText()).isEqualTo(manager.getId().toString());
        admin.setEnabled(false);users.saveAndFlush(admin);process(id);
        assertThat(jdbc.queryForObject("select count(*) from recurring_invoice_runs",Integer.class)).isEqualTo(1);
    }
    @Test void invalidCommercialModelsAreRejectedBeforeCreatingDocuments() throws Exception {
        var empty=new HashMap<String,Object>(document(client.getId()));empty.put("lines",List.of());
        request(post("/api/v1/quotations"),token,Map.of("document",empty,"validUntil","2026-09-01"),400);
        var missing=new HashMap<String,Object>(document(client.getId()));missing.remove("lines");
        request(post("/api/v1/quotations"),token,Map.of("document",missing,"validUntil","2026-09-01"),400);
        var duplicate=new HashMap<String,Object>(document(client.getId()));duplicate.put("lines",List.of(((List<?>)duplicate.get("lines")).get(0),((List<?>)duplicate.get("lines")).get(0)));
        request(post("/api/v1/quotations"),token,Map.of("document",duplicate,"validUntil","2026-09-01"),400);
        for (var line : List.of(Map.of("productId",product.getId(),"quantity",1,"vatRate",20),Map.of("productId",product.getId(),"quantity",1,"unitPriceExcludingTax",100))) {
            var data=new HashMap<String,Object>(document(client.getId()));data.put("lines",List.of(line));
            request(post("/api/v1/quotations"),token,Map.of("document",data,"validUntil","2026-09-01"),400);
        }
        var unsupported=new HashMap<String,Object>(document(client.getId()));unsupported.put("paymentMethod","CARD");
        request(post("/api/v1/quotations"),token,Map.of("document",unsupported,"validUntil","2026-09-01"),400);
        request(post("/api/v1/quotations"),token,Map.of("document",document(client.getId()),"validUntil","2026-07-01"),400);
        product.setStatus(false,admin.getId());products.saveAndFlush(product);
        request(post("/api/v1/quotations"),token,Map.of("document",document(client.getId()),"validUntil","2026-09-01"),409);
        client.setStatus(false,admin.getId());clients.saveAndFlush(client);
        request(post("/api/v1/quotations"),token,Map.of("document",document(client.getId()),"validUntil","2026-09-01"),409);
        assertThat(jdbc.queryForObject("select count(*) from quotations",Integer.class)).isZero();
    }
    @Test void dueDatesPaymentDatesAndCorrectionsHaveExplicitGuards() throws Exception {
        var i=invoice(client.getId());String path="/api/v1/invoices/"+i.path("id").asText();
        request(put(path+"/due-date"),token,Map.of("version",i.path("version").asLong(),"dueDate","2026-07-01"),400);
        request(post(path+"/payments"),token,Map.of("version",i.path("version").asLong(),"amount",10,"paymentDate","2099-01-01","paymentMethod","CASH","reference",""),400);
        request(post(path+"/payments"),token,Map.of("version",i.path("version").asLong(),"amount",10,"paymentDate","2026-08-03","paymentMethod","CARD","reference",""),400);
        var paid=pay(i,"216",200);String paymentId=request(get(path+"/payments"),token,null,200).get(0).path("id").asText();
        request(post(path+"/payments"),token,Map.of("version",paid.path("version").asLong(),"amount",1,"paymentDate","2026-08-03","paymentMethod","CASH","reference",""),409);
        var reversed=request(post(path+"/payments/"+paymentId+"/reverse"),token,Map.of("version",paid.path("version").asLong(),"reason","Correction"),200);
        request(post(path+"/payments/"+paymentId+"/reverse"),token,Map.of("version",reversed.path("version").asLong(),"reason","Correction"),409);
        var cancelled=request(post(path+"/cancel"),token,Map.of("version",reversed.path("version").asLong()),200);
        request(put(path+"/due-date"),token,Map.of("version",cancelled.path("version").asLong(),"dueDate","2026-08-10"),409);
        request(post(path+"/payments/"+paymentId+"/reverse"),token,Map.of("version",cancelled.path("version").asLong(),"reason","Correction"),409);
    }
    @Test void portalRotationDownloadsAndClientDeactivationRespectTheSameScope() throws Exception {
        var i=invoice(client.getId());var partial=pay(i,"50",200);
        String path="/api/v1/clients/"+client.getId()+"/portal-access";
        assertThat(request(get(path),token,null,200).path("active").asBoolean()).isFalse();
        var first=request(post(path),token,Map.of("days",7),200);
        assertThat(request(get(path),token,null,200).path("active").asBoolean()).isTrue();
        String secret=request(post(path),token,Map.of("days",7),200).path("token").asText();
        request(get("/api/v1/portal/invoices"),"Portal "+first.path("token").asText(),null,401);
        request(get("/api/v1/portal/invoices"),"Portal "+"A".repeat(43),null,401);
        var payments=request(get("/api/v1/portal/invoices/"+i.path("id").asText()+"/payments"),"Portal "+secret,null,200);
        assertThat(payments.get(0).path("amount").decimalValue()).isEqualByComparingTo("50");assertThat(payments.get(0).has("createdBy")).isFalse();
        var response=mvc.perform(get("/api/v1/portal/invoices/"+i.path("id").asText()+"/pdf").header(HttpHeaders.AUTHORIZATION,"Portal "+secret)).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(new String(response.getContentAsByteArray(),java.nio.charset.StandardCharsets.ISO_8859_1)).startsWith("%PDF-");
        client.setStatus(false,admin.getId());clients.saveAndFlush(client);
        request(get("/api/v1/portal/invoices"),"Portal "+secret,null,401);
        request(post(path),token,Map.of("days",7),409);
    }
    @Test void recurringFuturePausedAndCompletedOccurrencesHaveDistinctGuards() throws Exception {
        var i=invoice(client.getId());
        var r=request(post("/api/v1/recurring-invoices"),token,Map.of("invoiceId",i.path("id").asText(),"title","Une occurrence","startDate","2026-09-01","endDate","2026-09-01","intervalMonths",1,"paymentTermsDays",30),200);
        UUID id=UUID.fromString(r.path("id").asText());String path="/api/v1/recurring-invoices/"+id;
        process(id);request(post(path+"/generate"),token,Map.of("version",0),409);
        var paused=request(put(path+"/status"),token,Map.of("version",0,"enabled",false),200);
        process(id);request(post(path+"/generate"),token,Map.of("version",paused.path("version").asLong()),409);
        request(put(path+"/status"),token,Map.of("version",0,"enabled",true),409);
        request(post(path+"/generate"),otherToken,Map.of("version",0),404);
        request(put(path+"/status"),token,Map.of("version",paused.path("version").asLong(),"enabled",true),200);
        clock.set("2026-09-01");process(id);
        var done=request(get("/api/v1/recurring-invoices"),token,null,200).path("content").get(0);
        assertThat(done.path("enabled").asBoolean()).isFalse();
        request(put(path+"/status"),token,Map.of("version",done.path("version").asLong(),"enabled",true),409);
        recurring.process(id,0);recurring.recordFailure(id,0,"Ancienne erreur");
        assertThat(jdbc.queryForObject("select last_error from recurring_invoices where id=?",String.class,id)).isNull();
        assertThat(jdbc.queryForObject("select count(*) from recurring_invoice_runs",Integer.class)).isEqualTo(1);
    }
    @Test void invalidRecurrencePeriodsAndCancelledSourcesAreRejected() throws Exception {
        var i=invoice(client.getId());
        var input=new HashMap<String,Object>(Map.of("invoiceId",i.path("id").asText(),"title","Mensuel","startDate","2026-09-01","endDate","2026-08-01","intervalMonths",1,"paymentTermsDays",30));
        request(post("/api/v1/recurring-invoices"),token,input,400);
        input.remove("endDate");
        request(post("/api/v1/invoices/"+i.path("id").asText()+"/cancel"),token,Map.of("version",i.path("version").asLong()),200);
        request(post("/api/v1/recurring-invoices"),token,input,409);
    }
}

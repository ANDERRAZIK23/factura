package ma.facture.invoicing.sales;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.*;

@Component
@ConditionalOnProperty(name = "app.invoices.recurring-job.enabled", havingValue = "true", matchIfMissing = true)
public class RecurringInvoiceScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(RecurringInvoiceScheduler.class);
    private final RecurringInvoiceRepository schedules;
    private final RecurringInvoiceService service;
    private final Clock clock;
    public RecurringInvoiceScheduler(RecurringInvoiceRepository schedules, RecurringInvoiceService service, Clock clock) { this.schedules = schedules; this.service = service; this.clock = clock; }
    @Scheduled(cron = "${app.invoices.recurring-job.cron:0 10 * * * *}")
    public void run() {
        for (var schedule : schedules.due(LocalDate.now(clock), PageRequest.of(0, 100))) {
            var id = schedule.getId();
            try { service.process(id, schedule.getVersion()); }
            catch (RuntimeException exception) {
                LOG.error("Recurring invoice preparation failed scheduleId={}", id, exception);
                String message = exception instanceof ma.facture.invoicing.common.ConflictException
                        || exception instanceof ma.facture.invoicing.common.ValidationException
                        || exception instanceof ma.facture.invoicing.invoice.exception.InvoiceValidationException
                        ? exception.getMessage() : "La préparation a échoué. Corrigez le modèle puis reprenez la récurrence";
                service.recordFailure(id, schedule.getVersion(), message);
            }
        }
    }
}

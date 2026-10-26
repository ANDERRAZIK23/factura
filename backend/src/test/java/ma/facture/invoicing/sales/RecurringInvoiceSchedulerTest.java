package ma.facture.invoicing.sales;
import ma.facture.invoicing.common.*;
import ma.facture.invoicing.invoice.exception.InvoiceValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.domain.Pageable;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RecurringInvoiceSchedulerTest {
    static Stream<RuntimeException> failures() { return Stream.of(new ConflictException("Client désactivé"),
            new ValidationException("Modèle invalide"), new InvoiceValidationException("Article invalide")); }
    @ParameterizedTest @MethodSource("failures")
    void reportsBusinessFailureAndContinuesWithTheNextSchedule(RuntimeException error) {
        var repository=mock(RecurringInvoiceRepository.class);var service=mock(RecurringInvoiceService.class);
        var first=mock(RecurringInvoice.class);var second=mock(RecurringInvoice.class);
        UUID one=UUID.randomUUID(),two=UUID.randomUUID();
        when(first.getId()).thenReturn(one);when(first.getVersion()).thenReturn(3L);when(second.getId()).thenReturn(two);
        when(repository.due(any(LocalDate.class),any(Pageable.class))).thenReturn(List.of(first,second));
        doThrow(error).when(service).process(one,3);
        new RecurringInvoiceScheduler(repository,service,Clock.systemUTC()).run();
        verify(service).recordFailure(one,3,error.getMessage());verify(service).process(two,0);
    }
    @Test void doesNotExposeTechnicalFailuresInTheBusinessInterface() {
        var repository=mock(RecurringInvoiceRepository.class);var service=mock(RecurringInvoiceService.class);
        var schedule=mock(RecurringInvoice.class);UUID id=UUID.randomUUID();when(schedule.getId()).thenReturn(id);
        when(repository.due(any(LocalDate.class),any(Pageable.class))).thenReturn(List.of(schedule));
        doThrow(new IllegalStateException("database password and SQL details")).when(service).process(id,0);
        new RecurringInvoiceScheduler(repository,service,Clock.systemUTC()).run();
        verify(service).recordFailure(eq(id),eq(0L),argThat(message -> !message.contains("password") && message.contains("préparation a échoué")));
    }
}

package ma.facture.invoicing.common;

import ma.facture.invoicing.client.exception.*;
import ma.facture.invoicing.product.exception.*;
import ma.facture.invoicing.contract.exception.*;
import ma.facture.invoicing.invoice.exception.*;
import ma.facture.invoicing.invoice.pdf.InvoicePdfException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import java.net.URI;
import java.util.LinkedHashMap;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail authentication(AuthenticationException exception) {
        return problem(HttpStatus.UNAUTHORIZED, "Échec de l’authentification", exception.getMessage());
    }
    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException exception) { return problem(HttpStatus.CONFLICT, "Conflit", exception.getMessage()); }
    @ExceptionHandler(ValidationException.class)
    ProblemDetail validation(ValidationException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Échec de la validation", exception.getMessage());
    }
    @ExceptionHandler(DuplicateClientIceException.class)
    ProblemDetail duplicateClientIce(DuplicateClientIceException exception) {
        return problem(HttpStatus.CONFLICT, "Le client existe déjà", exception.getMessage(), "duplicate-client-ice");
    }
    @ExceptionHandler({ClientConcurrencyException.class, ObjectOptimisticLockingFailureException.class})
    ProblemDetail clientConcurrency(Exception exception) {
        return problem(HttpStatus.CONFLICT, "Le client a été modifié", "Ce client a été modifié par un autre utilisateur. Rechargez les dernières données et réessayez", "client-concurrency");
    }
    @ExceptionHandler(InvalidClientQueryException.class)
    ProblemDetail invalidClientQuery(InvalidClientQueryException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Requête client invalide", exception.getMessage(), "invalid-client-query");
    }
    @ExceptionHandler(ClientCodeGenerationException.class)
    ProblemDetail clientCodeGeneration(ClientCodeGenerationException exception) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Code client indisponible", "Impossible d’attribuer un code client", "client-code-unavailable");
    }
    @ExceptionHandler(ProductConcurrencyException.class)
    ProblemDetail productConcurrency(ProductConcurrencyException exception) {
        return problem(HttpStatus.CONFLICT, "Le produit a été modifié", exception.getMessage(), "product-concurrency");
    }
    @ExceptionHandler(InvalidProductQueryException.class)
    ProblemDetail invalidProductQuery(InvalidProductQueryException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Requête produit invalide", exception.getMessage(), "invalid-product-query");
    }
    @ExceptionHandler(ProductValidationException.class)
    ProblemDetail productValidation(ProductValidationException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Produit invalide", exception.getMessage(), "invalid-product");
    }
    @ExceptionHandler(ProductCodeGenerationException.class)
    ProblemDetail productCodeGeneration(ProductCodeGenerationException exception) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Code produit indisponible", "Impossible d’attribuer un code produit", "product-code-unavailable");
    }
    @ExceptionHandler(ContractConcurrencyException.class)
    ProblemDetail contractConcurrency(ContractConcurrencyException exception) {
        return problem(HttpStatus.CONFLICT, "Le contrat a été modifié", exception.getMessage(), "contract-concurrency");
    }
    @ExceptionHandler(ContractConflictException.class)
    ProblemDetail contractConflict(ContractConflictException exception) {
        return problem(HttpStatus.CONFLICT, "Conflit sur le contrat", exception.getMessage(), "contract-conflict");
    }
    @ExceptionHandler(ContractValidationException.class)
    ProblemDetail contractValidation(ContractValidationException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Contrat invalide", exception.getMessage(), "invalid-contract");
    }
    @ExceptionHandler(InvalidContractQueryException.class)
    ProblemDetail invalidContractQuery(InvalidContractQueryException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Requête contrat invalide", exception.getMessage(), "invalid-contract-query");
    }
    @ExceptionHandler(InvoiceConcurrencyException.class)
    ProblemDetail invoiceConcurrency(InvoiceConcurrencyException exception) {
        return problem(HttpStatus.CONFLICT, "La facture a été modifiée", exception.getMessage(), "invoice-concurrency");
    }
    @ExceptionHandler(InvoiceConflictException.class)
    ProblemDetail invoiceConflict(InvoiceConflictException exception) {
        return problem(HttpStatus.CONFLICT, "Conflit sur la facture", exception.getMessage(), "invoice-conflict");
    }
    @ExceptionHandler({InvoiceValidationException.class, InvoiceCalculationException.class})
    ProblemDetail invoiceValidation(RuntimeException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Facture invalide", exception.getMessage(), "invalid-invoice");
    }
    @ExceptionHandler(InvalidInvoiceQueryException.class)
    ProblemDetail invalidInvoiceQuery(InvalidInvoiceQueryException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Requête facture invalide", exception.getMessage(), "invalid-invoice-query");
    }
    @ExceptionHandler(InvoicePdfException.class)
    ProblemDetail invoicePdf(InvoicePdfException exception) {
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "PDF de facture indisponible",
                "Impossible de générer le PDF de la facture", "invoice-pdf-unavailable");
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrityViolation(DataIntegrityViolationException exception) {
        return problem(HttpStatus.CONFLICT, "Conflit de données", "La modification demandée est incompatible avec les données enregistrées", "data-conflict");
    }
    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException exception) { return problem(HttpStatus.NOT_FOUND, "Ressource introuvable", exception.getMessage()); }
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        var fields = new LinkedHashMap<String, String>();
        exception.getBindingResult().getFieldErrors().forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        var problem = problem(HttpStatus.BAD_REQUEST, "Échec de la validation", "Un ou plusieurs champs sont invalides");
        problem.setProperty("fieldErrors", fields);
        problem.setProperty("errors", fields);
        return handleExceptionInternal(exception, problem, headers, status, request);
    }
    private ProblemDetail problem(HttpStatus status, String title, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail); problem.setTitle(title);
        problem.setType(URI.create("https://invoice-management.local/problems/" + status.value())); return problem;
    }
    private ProblemDetail problem(HttpStatus status, String title, String detail, String type) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail); problem.setTitle(title);
        problem.setType(URI.create("https://invoice-management.local/problems/" + type)); return problem;
    }
}

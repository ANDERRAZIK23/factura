package ma.facture.invoicing.product.service;

import ma.facture.invoicing.product.dto.*;
import ma.facture.invoicing.product.entity.*;
import ma.facture.invoicing.product.exception.*;
import ma.facture.invoicing.product.mapper.ProductMapper;
import ma.facture.invoicing.product.repository.ProductRepository;
import ma.facture.invoicing.security.AuthenticatedUser;
import ma.facture.invoicing.user.UserRole;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.*;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductServiceTest {
    private final ProductRepository repository = mock(ProductRepository.class);
    private final ProductCodeGenerator codes = mock(ProductCodeGenerator.class);
    private final ProductPricingService pricing = new ProductPricingService();
    private final ProductMapper mapper = new ProductMapper(pricing);
    private final ProductService service = new ProductService(repository, codes, pricing, mapper);
    private final AuthenticatedUser actor = new AuthenticatedUser(UUID.randomUUID(), "admin@example.com", UserRole.ADMIN);

    @BeforeEach void saveReturnsArgument() { when(repository.saveAndFlush(any(Product.class))).thenAnswer(call -> call.getArgument(0)); }

    @Test void createsNormalizesAndAuditsProduct() {
        when(codes.generate()).thenReturn("PRD-000001");
        ProductResponse response = service.create(new ProductCreateRequest("  Blue Roll  ", "  Reference  ", new BigDecimal("13.005"), new BigDecimal("19.995"), ProductUnit.ROLL), actor);
        assertThat(response.productCode()).isEqualTo("PRD-000001");
        assertThat(response.productName()).isEqualTo("Blue Roll");
        assertThat(response.description()).isEqualTo("Reference");
        assertThat(response.unitPriceExcludingTax()).isEqualByComparingTo("13.01");
        assertThat(response.defaultVatRate()).isEqualByComparingTo("20.00");
        assertThat(response.unitPriceIncludingTax()).isEqualByComparingTo("15.61");
        assertThat(response.createdBy()).isEqualTo(actor.id());
    }

    @Test void retriesOnlyKnownCodeConflicts() {
        when(codes.generate()).thenReturn("PRD-000010", "PRD-000011");
        when(repository.saveAndFlush(any(Product.class))).thenThrow(new DataIntegrityViolationException("products_product_code_key"))
                .thenAnswer(call -> call.getArgument(0));
        assertThat(service.create(request("Name"), actor).productCode()).isEqualTo("PRD-000011");

        reset(repository, codes); when(codes.generate()).thenReturn("PRD-000020");
        when(repository.saveAndFlush(any(Product.class))).thenThrow(new DataIntegrityViolationException("other_constraint"));
        assertThatThrownBy(() -> service.create(request("Name"), actor)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void updatesEditableFieldsWithoutChangingCode() {
        Product product = product("PRD-000100", "Old"); when(repository.findByIdAndCompanyId(product.getId(), actor.companyId())).thenReturn(Optional.of(product));
        ProductResponse response = service.update(product.getId(), new ProductUpdateRequest(" Updated ", " ", new BigDecimal("9.995"), new BigDecimal("5.555"), ProductUnit.SERVICE, 0), actor);
        assertThat(response.productCode()).isEqualTo("PRD-000100");
        assertThat(response.productName()).isEqualTo("Updated");
        assertThat(response.description()).isNull();
        assertThat(response.unitPriceExcludingTax()).isEqualByComparingTo("10.00");
        assertThat(response.defaultVatRate()).isEqualByComparingTo("5.56");
        assertThat(response.updatedBy()).isEqualTo(actor.id());
    }

    @Test void rejectsStaleUpdatesAndMissingProducts() {
        Product product = product("PRD-000101", "Name"); when(repository.findByIdAndCompanyId(product.getId(), actor.companyId())).thenReturn(Optional.of(product));
        assertThatThrownBy(() -> service.update(product.getId(), new ProductUpdateRequest("Name", null, BigDecimal.ONE, BigDecimal.ZERO, ProductUnit.PIECE, 1), actor)).isInstanceOf(ProductConcurrencyException.class);
        UUID missing = UUID.randomUUID(); when(repository.findByIdAndCompanyId(missing, actor.companyId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(missing)).isInstanceOf(ProductNotFoundException.class);
    }

    @Test void changesStatusIdempotentlyAndChecksVersion() {
        Product product = product("PRD-000102", "Name"); when(repository.findByIdAndCompanyId(product.getId(), actor.companyId())).thenReturn(Optional.of(product));
        assertThat(service.updateStatus(product.getId(), new ProductStatusRequest(false, 0), actor).active()).isFalse();
        clearInvocations(repository);
        assertThat(service.updateStatus(product.getId(), new ProductStatusRequest(false, 99), actor).active()).isFalse();
        verify(repository, never()).saveAndFlush(any());
        assertThatThrownBy(() -> service.updateStatus(product.getId(), new ProductStatusRequest(true, 2), actor)).isInstanceOf(ProductConcurrencyException.class);
    }

    @Test void validatesPagingSortingUnitsAndPriceRanges() {
        assertThat(ProductService.normalizeSearch("  ROLL ")).isEqualTo("roll");
        assertThat(ProductService.normalizeSearch(" ")).isNull();
        assertThat(ProductService.parseSort(null)).isEqualTo(Sort.by(Sort.Direction.ASC, "productName"));
        assertThat(ProductService.parseSort("unitPriceExcludingTax,desc")).isEqualTo(Sort.by(Sort.Direction.DESC, "unitPriceExcludingTax"));
        assertThat(ProductService.parseUnit(" roll ")).isEqualTo(ProductUnit.ROLL);
        assertThatThrownBy(() -> ProductService.parseSort("description,asc")).isInstanceOf(InvalidProductQueryException.class);
        assertThatThrownBy(() -> ProductService.parseSort("productName,sideways")).isInstanceOf(InvalidProductQueryException.class);
        assertThatThrownBy(() -> ProductService.parseUnit("BOX")).isInstanceOf(InvalidProductQueryException.class);
        assertThatThrownBy(() -> service.list(-1, 20, null, null, null, null, null, null)).isInstanceOf(InvalidProductQueryException.class);
        assertThatThrownBy(() -> service.list(0, 101, null, null, null, null, null, null)).isInstanceOf(InvalidProductQueryException.class);
        assertThatThrownBy(() -> service.list(0, 20, null, null, null, null, BigDecimal.TEN, BigDecimal.ONE)).isInstanceOf(InvalidProductQueryException.class);
    }

    @Test void listsOnlyRequestedFilteredPage() {
        Product product = product("PRD-000103", "Roll");
        when(repository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(product), PageRequest.of(1, 2), 3));
        ProductPageResponse page = service.list(1, 2, "productCode,asc", "Roll", true, "ROLL", BigDecimal.ZERO, new BigDecimal("20"));
        assertThat(page.content()).extracting(ProductSummaryResponse::productCode).containsExactly("PRD-000103");
        assertThat(page.page()).isEqualTo(1); assertThat(page.totalElements()).isEqualTo(3);
    }

    @Test void validatesRequiredServiceInputs() {
        when(codes.generate()).thenReturn("PRD-000104");
        assertThatThrownBy(() -> service.create(new ProductCreateRequest(" ", null, BigDecimal.ONE, BigDecimal.ZERO, ProductUnit.PIECE), actor)).isInstanceOf(ProductValidationException.class);
        assertThatThrownBy(() -> service.create(new ProductCreateRequest("Name", null, BigDecimal.ONE, BigDecimal.ZERO, null), actor)).isInstanceOf(ProductValidationException.class);
    }

    private static ProductCreateRequest request(String name) { return new ProductCreateRequest(name, null, BigDecimal.ONE, new BigDecimal("20"), ProductUnit.PIECE); }
    private Product product(String code, String name) { return new Product(code, name, "Description", new BigDecimal("10.00"), new BigDecimal("20.00"), ProductUnit.ROLL, actor.id()); }
}

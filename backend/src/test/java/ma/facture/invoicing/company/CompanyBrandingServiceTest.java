package ma.facture.invoicing.company;

import ma.facture.invoicing.common.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;
import java.util.Base64;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CompanyBrandingServiceTest {
    private final CompanyRepository companies = mock(CompanyRepository.class);
    private final CompanyBrandingRepository branding = mock(CompanyBrandingRepository.class);
    private final CompanyBrandingService service = new CompanyBrandingService(companies, branding);

    @Test void storesAValidatedPngAndReturnsAnEmbeddableDataUrl() throws Exception {
        Company company = new Company("ENT-000002", "Atlas", null, null, null, null);
        byte[] png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn((long) png.length);
        when(file.getBytes()).thenReturn(png);
        when(companies.findById(company.getId())).thenReturn(Optional.of(company));
        when(branding.findById(company.getId())).thenReturn(Optional.empty());
        when(branding.saveAndFlush(any(CompanyBranding.class))).thenAnswer(call -> call.getArgument(0));

        CompanyBrandingDtos.Response response = service.upload(company.getId(), file);

        assertThat(response.companyName()).isEqualTo("Atlas");
        assertThat(response.logoDataUrl()).startsWith("data:image/png;base64,");
        verify(branding).saveAndFlush(any(CompanyBranding.class));
    }

    @Test void rejectsOversizedOrUnsupportedLogoContent() throws Exception {
        Company company = new Company("ENT-000002", "Atlas", null, null, null, null);
        when(companies.findById(company.getId())).thenReturn(Optional.of(company));
        MultipartFile oversized = mock(MultipartFile.class);
        when(oversized.isEmpty()).thenReturn(false);
        when(oversized.getSize()).thenReturn(CompanyBrandingService.MAX_LOGO_BYTES + 1);
        assertThatThrownBy(() -> service.upload(company.getId(), oversized))
                .isInstanceOf(ValidationException.class).hasMessageContaining("512 Ko");

        MultipartFile text = mock(MultipartFile.class);
        when(text.isEmpty()).thenReturn(false);
        when(text.getSize()).thenReturn(4L);
        when(text.getBytes()).thenReturn(new byte[] {'t', 'e', 'x', 't'});
        assertThatThrownBy(() -> service.upload(company.getId(), text))
                .isInstanceOf(ValidationException.class).hasMessageContaining("PNG ou JPEG");
    }
}

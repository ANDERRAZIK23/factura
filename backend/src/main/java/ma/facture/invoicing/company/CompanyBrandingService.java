package ma.facture.invoicing.company;

import ma.facture.invoicing.common.NotFoundException;
import ma.facture.invoicing.common.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.UUID;

@Service
public class CompanyBrandingService {
    static final long MAX_LOGO_BYTES = 512 * 1024;
    private final CompanyRepository companies;
    private final CompanyBrandingRepository branding;

    public CompanyBrandingService(CompanyRepository companies, CompanyBrandingRepository branding) {
        this.companies = companies;
        this.branding = branding;
    }

    @Transactional(readOnly = true)
    public CompanyBrandingDtos.Response get(UUID companyId) {
        Company company = findCompany(companyId);
        return branding.findById(companyId)
                .map(value -> response(company, value))
                .orElseGet(() -> new CompanyBrandingDtos.Response(company.getCompanyName(), null, null));
    }

    @Transactional
    public CompanyBrandingDtos.Response upload(UUID companyId, MultipartFile file) {
        Company company = findCompany(companyId);
        byte[] content = readAndValidate(file);
        String contentType = detectContentType(content);
        CompanyBranding value = branding.findById(companyId).orElse(null);
        if (value == null) value = new CompanyBranding(companyId, contentType, content);
        else value.update(contentType, content);
        return response(company, branding.saveAndFlush(value));
    }

    @Transactional
    public void remove(UUID companyId) {
        findCompany(companyId);
        branding.deleteById(companyId);
    }

    private Company findCompany(UUID companyId) {
        return companies.findById(companyId).orElseThrow(() -> new NotFoundException("Entreprise introuvable"));
    }

    private static byte[] readAndValidate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ValidationException("Le fichier du logo est requis");
        if (file.getSize() > MAX_LOGO_BYTES) throw new ValidationException("Le logo ne doit pas dépasser 512 Ko");
        try { return file.getBytes(); }
        catch (IOException exception) { throw new ValidationException("Impossible de lire le logo"); }
    }

    private static String detectContentType(byte[] content) {
        String contentType;
        if (content.length >= 8 && (content[0] & 0xff) == 0x89 && content[1] == 0x50 && content[2] == 0x4e
                && content[3] == 0x47 && content[4] == 0x0d && content[5] == 0x0a && content[6] == 0x1a && content[7] == 0x0a)
            contentType = "image/png";
        else if (content.length >= 3 && (content[0] & 0xff) == 0xff && (content[1] & 0xff) == 0xd8 && (content[2] & 0xff) == 0xff)
            contentType = "image/jpeg";
        else throw new ValidationException("Le logo doit être une image PNG ou JPEG");
        validateDimensions(content);
        return contentType;
    }

    private static void validateDimensions(byte[] content) {
        try {
            var image = ImageIO.read(new ByteArrayInputStream(content));
            if (image == null) throw new ValidationException("Le logo doit être une image PNG ou JPEG valide");
            if (image.getWidth() > 2048 || image.getHeight() > 2048)
                throw new ValidationException("Les dimensions du logo ne doivent pas dépasser 2 048 × 2 048 pixels");
        } catch (IOException exception) {
            throw new ValidationException("Le logo doit être une image PNG ou JPEG valide");
        }
    }

    private static CompanyBrandingDtos.Response response(Company company, CompanyBranding value) {
        String encoded = Base64.getEncoder().encodeToString(value.getLogoData());
        return new CompanyBrandingDtos.Response(company.getCompanyName(),
                "data:" + value.getLogoContentType() + ";base64," + encoded, value.getUpdatedAt());
    }
}

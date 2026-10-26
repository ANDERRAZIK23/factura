package ma.facture.invoicing;

import ma.facture.invoicing.config.InvoiceStorageProperties;
import ma.facture.invoicing.security.SecurityProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.scheduling.annotation.EnableScheduling;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

@SpringBootApplication
@EnableConfigurationProperties({InvoiceStorageProperties.class, SecurityProperties.class})
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
@EnableScheduling
public class InvoiceManagementApplication {
    public static void main(String[] args) {
        SpringApplication.run(InvoiceManagementApplication.class, args);
    }
}

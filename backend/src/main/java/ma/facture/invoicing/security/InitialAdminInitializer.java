package ma.facture.invoicing.security;

import ma.facture.invoicing.user.*;
import org.slf4j.*;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class InitialAdminInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(InitialAdminInitializer.class);
    private final SecurityProperties properties; private final UserRepository users; private final PasswordService passwords; private final Environment environment;
    public InitialAdminInitializer(SecurityProperties properties, UserRepository users, PasswordService passwords, Environment environment) {
        this.properties = properties; this.users = users; this.passwords = passwords; this.environment = environment;
    }
    @Override @Transactional public void run(ApplicationArguments args) {
        boolean missing = blank(properties.initialAdminName()) || blank(properties.initialAdminEmail()) || blank(properties.initialAdminPassword());
        if (missing) { if (environment.matchesProfiles("prod")) throw new IllegalStateException("Initial administrator configuration is required in production"); return; }
        if (!properties.initialAdminPassword().matches(UserDtos.STRONG_PASSWORD)) {
            throw new IllegalStateException("Initial administrator password must be 12 to 128 characters and include upper-case, lower-case, numeric, and special characters");
        }
        String email = UserService.normalizeEmail(properties.initialAdminEmail());
        if (users.existsByEmailIgnoreCase(email)) return;
        var admin = users.save(new UserEntity(properties.initialAdminName().strip(), email, passwords.hash(properties.initialAdminPassword()), UserRole.APP_ADMIN));
        log.info("Initial administrator created id={}", admin.getId());
    }
    private boolean blank(String value) { return value == null || value.isBlank(); }
}

package ma.facture.invoicing.security;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordService {
    private final PasswordEncoder encoder;
    public PasswordService(PasswordEncoder encoder) { this.encoder = encoder; }
    public String hash(String password) { return encoder.encode(password); }
    public boolean matches(String password, String hash) { return encoder.matches(password, hash); }
}


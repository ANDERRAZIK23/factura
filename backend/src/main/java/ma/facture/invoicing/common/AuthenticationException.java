package ma.facture.invoicing.common;

public class AuthenticationException extends RuntimeException {
    public AuthenticationException(String message) { super(message); }
    public static AuthenticationException invalidCredentials() { return new AuthenticationException("Adresse e-mail ou mot de passe incorrect"); }
    public static AuthenticationException invalidSession() { return new AuthenticationException("Session invalide ou expirée"); }
}

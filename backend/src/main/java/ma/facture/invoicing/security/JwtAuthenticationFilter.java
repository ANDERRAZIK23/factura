package ma.facture.invoicing.security;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import ma.facture.invoicing.user.UserRepository;
import ma.facture.invoicing.company.CompanyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger LOGGER = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private final JwtService jwt; private final UserRepository users; private final CompanyRepository companies;
    public JwtAuthenticationFilter(JwtService jwt, UserRepository users, CompanyRepository companies) { this.jwt = jwt; this.users = users; this.companies = companies; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            try {
                var principal = jwt.validate(header.substring(7));
                if (users.findById(principal.id()).filter(user -> user.isEnabled() && user.getRole() == principal.role()
                        && user.getCompanyId().equals(principal.companyId())).isPresent()
                        && companies.findById(principal.companyId()).filter(company -> company.isEnabled()).isPresent()) {
                    var authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (RuntimeException exception) {
                LOGGER.debug("Bearer token authentication failed: {}", exception.getClass().getSimpleName());
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}

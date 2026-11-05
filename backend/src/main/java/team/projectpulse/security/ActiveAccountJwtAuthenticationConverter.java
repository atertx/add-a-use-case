package team.projectpulse.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import team.projectpulse.user.PeerEvaluationUser;
import team.projectpulse.user.UserRepository;

/**
 * Turns a verified bearer JWT into an authentication, but only for an account that is still active
 * (FR-SEC-active-account).
 *
 * <p>Runs on every bearer request, after the signature and expiry have been checked. It loads the user named by the
 * {@code userId} claim and throws {@link DisabledException} when that account is deactivated or no longer exists. The
 * bearer entry point hands the exception to {@code ExceptionHandlerAdvice}, which answers {@code 401}, and the SPA
 * returns to login. A token issued before a deactivation therefore stops working at its next request rather than at
 * its expiry. The cost is one primary-key read per request.
 *
 * <p>Authorities are still read from the token by the wrapped {@link JwtAuthenticationConverter}.
 */
public class ActiveAccountJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository userRepository;
    private final JwtAuthenticationConverter authoritiesConverter;


    public ActiveAccountJwtAuthenticationConverter(UserRepository userRepository, JwtAuthenticationConverter authoritiesConverter) {
        this.userRepository = userRepository;
        this.authoritiesConverter = authoritiesConverter;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Integer userId = readUserId(jwt);
        boolean active = userId != null && this.userRepository.findById(userId)
                .map(PeerEvaluationUser::isEnabled)
                .orElse(false);
        if (!active) {
            throw new DisabledException("The account is deactivated or no longer exists.");
        }
        return this.authoritiesConverter.convert(jwt);
    }

    private static Integer readUserId(Jwt jwt) {
        Object claim = jwt.getClaim("userId");
        if (claim instanceof Number number) {
            return number.intValue();
        }
        if (claim instanceof String text) {
            try {
                return Integer.valueOf(text);
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        return null;
    }

}

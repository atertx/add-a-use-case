package team.projectpulse.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import team.projectpulse.student.Student;
import team.projectpulse.user.UserRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ActiveAccountJwtAuthenticationConverterTest {

    @Mock
    UserRepository userRepository;

    private ActiveAccountJwtAuthenticationConverter converter() {
        return new ActiveAccountJwtAuthenticationConverter(this.userRepository, new JwtAuthenticationConverter());
    }

    private static Jwt jwt(Object userId) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("someone@abc.edu")
                .claim("userId", userId)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
    }

    @Test
    void testActiveAccountIsAuthenticated() {
        given(this.userRepository.findById(4)).willReturn(Optional.of(new Student("john", "John", "Smith", "j.smith@abc.edu", "", true, "student")));

        AbstractAuthenticationToken token = converter().convert(jwt(4L));

        assertThat(token).isNotNull();
        assertThat(token.getName()).isEqualTo("someone@abc.edu");
    }

    @Test
    void testDeactivatedAccountIsRefused() {
        Student nora = new Student("nora", "Nora", "Quinn", "n.quinn@abc.edu", "", true, "student");
        nora.deactivate(LocalDate.of(2023, 8, 16));
        given(this.userRepository.findById(21)).willReturn(Optional.of(nora));

        assertThat(catchThrowable(() -> converter().convert(jwt(21L)))).isInstanceOf(DisabledException.class);
    }

    @Test
    void testDeletedAccountIsRefused() {
        given(this.userRepository.findById(99)).willReturn(Optional.empty());

        assertThat(catchThrowable(() -> converter().convert(jwt(99L)))).isInstanceOf(DisabledException.class);
    }

    @Test
    void testUnreadableUserIdIsRefused() {
        assertThat(catchThrowable(() -> converter().convert(jwt("not-a-number")))).isInstanceOf(DisabledException.class);
        verifyNoInteractions(this.userRepository);
    }

}

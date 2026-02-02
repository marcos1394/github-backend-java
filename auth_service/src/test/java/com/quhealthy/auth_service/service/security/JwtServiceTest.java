package com.quhealthy.auth_service.service.security;

import com.quhealthy.auth_service.model.Consumer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class JwtServiceTest {

    @InjectMocks
    private JwtService jwtService;

    // Usamos una clave secreta de prueba (256 bits min) para los tests
    private static final String TEST_SECRET_KEY = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final long EXPIRATION_TIME = 1000 * 60 * 24; // 24 horas

    @BeforeEach
    void setUp() {
        // Inyectamos los valores de application.properties manualmente
        ReflectionTestUtils.setField(jwtService, "secretKey", TEST_SECRET_KEY);
        ReflectionTestUtils.setField(jwtService, "jwtExpiration", EXPIRATION_TIME);
    }

    @Test
    @DisplayName("generateToken: Debe generar un token válido para un usuario")
    void generateToken_ShouldReturnValidString() {
        Consumer user = Consumer.builder().email("test@demo.com").build();

        String token = jwtService.generateToken(user);

        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    @DisplayName("extractUsername: Debe extraer el email correcto del token")
    void extractUsername_ShouldReturnCorrectEmail() {
        Consumer user = Consumer.builder().email("john.doe@example.com").build();
        String token = jwtService.generateToken(user);

        String extractedUsername = jwtService.extractUsername(token);

        assertEquals("john.doe@example.com", extractedUsername);
    }

    @Test
    @DisplayName("isTokenValid: Debe retornar true si el token pertenece al usuario y no ha expirado")
    void isTokenValid_ShouldReturnTrue_WhenTokenIsValid() {
        Consumer user = Consumer.builder().email("valid@user.com").build();
        String token = jwtService.generateToken(user);

        boolean isValid = jwtService.isTokenValid(token, user);

        assertTrue(isValid);
    }

    @Test
    @DisplayName("isTokenValid: Debe retornar false si el token pertenece a otro usuario")
    void isTokenValid_ShouldReturnFalse_WhenUserDoesNotMatch() {
        Consumer user1 = Consumer.builder().email("user1@test.com").build();
        Consumer user2 = Consumer.builder().email("user2@test.com").build();

        String token = jwtService.generateToken(user1);

        boolean isValid = jwtService.isTokenValid(token, user2); // Validamos contra user2

        assertFalse(isValid);
    }

    @Test
    @DisplayName("isTokenValid: Debe retornar false si el token ha expirado")
    void isTokenValid_ShouldReturnFalse_WhenExpired() {
        // Generamos un token manualmente que expiró hace 10 minutos
        Consumer user = Consumer.builder().email("expired@user.com").build();

        String expiredToken = Jwts.builder()
                .setClaims(new HashMap<>())
                .setSubject(user.getUsername())
                .setIssuedAt(new Date(System.currentTimeMillis() - 1000 * 60 * 60)) // Hace 1 hora
                .setExpiration(new Date(System.currentTimeMillis() - 1000 * 60 * 10)) // Expiró hace 10 min
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();

        // Esperamos que lance excepción o retorne false según tu implementación.
        // JJWT suele lanzar ExpiredJwtException al intentar parsearlo.
        assertThrows(io.jsonwebtoken.ExpiredJwtException.class, () -> {
            jwtService.isTokenValid(expiredToken, user);
        });
    }

    private Key getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(TEST_SECRET_KEY);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
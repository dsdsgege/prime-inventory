package hu.primemobile.inventory;

import hu.primemobile.inventory.config.JwtService;
import hu.primemobile.inventory.domain.Role;
import hu.primemobile.inventory.domain.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InventoryApplicationTests {

    @Test
    void testJwtGenerationAndValidation() {
        JwtService jwtService = new JwtService("test_secret_key_that_is_at_least_256_bits_long_for_unit_tests_2026", 3600000);

        User user = User.builder()
                .username("testuser")
                .role(Role.ROLE_ADMIN)
                .fullName("Test User")
                .build();

        String token = jwtService.generateToken(user);
        assertNotNull(token);
        assertTrue(token.length() > 20);

        String username = jwtService.extractUsername(token);
        assertEquals("testuser", username);

        String role = jwtService.extractRole(token);
        assertEquals("ROLE_ADMIN", role);

        assertTrue(jwtService.isTokenValid(token, "testuser"));
        assertFalse(jwtService.isTokenValid(token, "wronguser"));
    }
}

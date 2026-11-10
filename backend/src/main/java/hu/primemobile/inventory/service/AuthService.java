package hu.primemobile.inventory.service;

import hu.primemobile.inventory.config.JwtService;
import hu.primemobile.inventory.domain.User;
import hu.primemobile.inventory.dto.AuthDto;
import hu.primemobile.inventory.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional(readOnly = true)
    public AuthDto.LoginResponse login(AuthDto.LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BadCredentialsException("Hibás felhasználónév vagy jelszó."));

        if (!user.isActive()) {
            throw new BadCredentialsException("A felhasználói fiók inaktív.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadCredentialsException("Hibás felhasználónév vagy jelszó.");
        }

        String token = jwtService.generateToken(user);
        log.info("User '{}' successfully authenticated", user.getUsername());

        return AuthDto.LoginResponse.builder()
                .token(token)
                .username(user.getUsername())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .build();
    }

    @Transactional(readOnly = true)
    public AuthDto.UserDto getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof User user) {
            return AuthDto.UserDto.builder()
                    .id(user.getId())
                    .username(user.getUsername())
                    .fullName(user.getFullName())
                    .role(user.getRole().name())
                    .active(user.isActive())
                    .build();
        }
        throw new IllegalStateException("Nincs bejelentkezett felhasználó.");
    }
}

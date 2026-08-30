package com.acme.salary.auth;

import com.acme.salary.auth.dto.LoginRequest;
import com.acme.salary.auth.dto.LoginResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        // Throws BadCredentialsException for either a wrong password or an unknown email (Spring
        // Security's DaoAuthenticationProvider hides which, by design) - handled centrally by
        // GlobalExceptionHandler as a 401.
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));

        String role = authentication.getAuthorities().stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .orElseThrow()
                .replace("ROLE_", "");

        JwtService.GeneratedToken generated = jwtService.generateToken(authentication.getName(), role);
        return new LoginResponse(generated.token(), generated.expiresAt());
    }
}

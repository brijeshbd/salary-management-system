package com.acme.salary.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HrUserDetailsService implements UserDetailsService {

    private final HrUserRepository hrUserRepository;

    @Override
    public UserDetails loadUserByUsername(String email) {
        HrUser user = hrUserRepository
                .findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("No HR user with email: " + email));

        return User.withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .authorities("ROLE_" + user.getRole().name())
                .build();
    }
}

package com.vasu.cardanalyzer.service;

import com.vasu.cardanalyzer.dto.LoginRequest;
import com.vasu.cardanalyzer.dto.SignupRequest;
import com.vasu.cardanalyzer.dto.UserDto;
import com.vasu.cardanalyzer.model.AuthProvider;
import com.vasu.cardanalyzer.model.Role;
import com.vasu.cardanalyzer.model.User;
import com.vasu.cardanalyzer.repository.UserRepository;
import com.vasu.cardanalyzer.security.JwtTokenProvider;
import com.vasu.cardanalyzer.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;

    public AuthResult signup(SignupRequest signupRequest) {
        String email = signupRequest.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email is already registered.");
        }

        User user = new User();
        user.setName(signupRequest.getName().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(signupRequest.getPassword()));
        user.setProvider(AuthProvider.LOCAL);
        user.setRole(Role.ROLE_USER);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());

        User savedUser = userRepository.save(user);

        String token = tokenProvider.generateToken(savedUser.getId(), savedUser.getEmail());

        return new AuthResult(UserDto.fromEntity(savedUser), token);
    }

    public AuthResult login(LoginRequest loginRequest) {
        String email = loginRequest.getEmail().trim().toLowerCase();

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, loginRequest.getPassword())
        );

        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
        User user = userPrincipal.getUser();

        String token = tokenProvider.generateToken(user.getId(), user.getEmail());

        return new AuthResult(UserDto.fromEntity(user), token);
    }

    public record AuthResult(UserDto user, String token) {}
}

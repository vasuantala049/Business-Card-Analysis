package com.vasu.cardanalyzer.security.oauth2;

import java.time.Instant;
import java.util.Optional;

import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import com.vasu.cardanalyzer.model.AuthProvider;
import com.vasu.cardanalyzer.model.Role;
import com.vasu.cardanalyzer.model.User;
import com.vasu.cardanalyzer.repository.UserRepository;
import com.vasu.cardanalyzer.security.UserPrincipal;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CustomOidcUserService extends OidcUserService {

    private final UserRepository userRepository;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);
        return processOidcUser(oidcUser);
    }

    private OidcUser processOidcUser(OidcUser oidcUser) {
        String email = oidcUser.getEmail();
        String name = oidcUser.getFullName();
        String googleId = oidcUser.getSubject();

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email not found from OIDC provider");
        }

        Optional<User> userOptional = userRepository.findByEmail(email);
        User user;

        if (userOptional.isPresent()) {
            user = userOptional.get();
            user.setName(name);
            user.setGoogleId(googleId);
            user.setProvider(AuthProvider.GOOGLE);
            user.setUpdatedAt(Instant.now());
            user = userRepository.save(user);
        } else {
            user = new User();
            user.setEmail(email);
            user.setName(name);
            user.setProvider(AuthProvider.GOOGLE);
            user.setGoogleId(googleId);
            user.setRole(Role.ROLE_USER);
            user.setCreatedAt(Instant.now());
            user.setUpdatedAt(Instant.now());
            user = userRepository.save(user);
        }

        return UserPrincipal.create(user, oidcUser.getClaims());
    }
}
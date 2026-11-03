package com.vasu.cardanalyzer.controller;

import com.vasu.cardanalyzer.dto.LoginRequest;
import com.vasu.cardanalyzer.dto.MessageResponse;
import com.vasu.cardanalyzer.dto.SignupRequest;
import com.vasu.cardanalyzer.dto.UserDto;
import com.vasu.cardanalyzer.security.CookieUtils;
import com.vasu.cardanalyzer.security.UserPrincipal;
import com.vasu.cardanalyzer.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@Valid @RequestBody SignupRequest signupRequest) {
        try {
            AuthService.AuthResult result = authService.signup(signupRequest);
            ResponseCookie cookie = CookieUtils.createJwtCookie(result.token(), 86400);

            return ResponseEntity.status(HttpStatus.CREATED)
                    .header(HttpHeaders.SET_COOKIE, cookie.toString())
                    .body(result.user());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new MessageResponse(e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            AuthService.AuthResult result = authService.login(loginRequest);
            ResponseCookie cookie = CookieUtils.createJwtCookie(result.token(), 86400);

            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookie.toString())
                    .body(result.user());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new MessageResponse("Invalid email or password."));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        ResponseCookie cleanCookie = CookieUtils.createCleanJwtCookie();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cleanCookie.toString())
                .body(new MessageResponse("Logged out successfully."));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@AuthenticationPrincipal UserPrincipal currentUser) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new MessageResponse("Not authenticated."));
        }
        return ResponseEntity.ok(UserDto.fromEntity(currentUser.getUser()));
    }
}

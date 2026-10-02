package com.vasu.cardanalyzer.security.oauth2;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.vasu.cardanalyzer.security.CookieUtils;
import com.vasu.cardanalyzer.security.JwtTokenProvider;
import com.vasu.cardanalyzer.security.UserPrincipal;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider tokenProvider;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        String token = tokenProvider.generateToken(userPrincipal.getId(), userPrincipal.getEmail());
        ResponseCookie cookie = CookieUtils.createJwtCookie(token, 86400);

        response.addHeader("Set-Cookie", cookie.toString());

        getRedirectStrategy().sendRedirect(request, response, frontendUrl + "/auth-callback");
    }
}

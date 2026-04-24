package com.finance.web;

import com.finance.common.Result;
import com.finance.model.entity.User;
import com.finance.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/register")
    public Result<Void> register(@RequestBody Map<String, String> body) {
        authService.register(body.get("username"), body.get("account"), body.get("password"));
        return Result.ok(null);
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody Map<String, Object> body, HttpServletResponse response) {
        String account = (String) body.get("account");
        String password = (String) body.get("password");
        boolean remember = Boolean.TRUE.equals(body.get("remember"));

        Map<String, Object> result = authService.login(account, password, remember);

        String accessToken = (String) result.remove("access_token");
        String refreshToken = (String) result.remove("refresh_token");

        addCookie(response, "access_token", accessToken, 30 * 60);
        addCookie(response, "refresh_token", refreshToken, remember ? 30 * 24 * 60 * 60 : 7 * 24 * 60 * 60);

        return Result.ok(result);
    }

    @PostMapping("/refresh")
    public Result<Void> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = getTokenFromCookie(request, "refresh_token");
        if (refreshToken == null) {
            return Result.fail(401, "refresh_token 不存在");
        }
        String newAccessToken = authService.refreshAccessToken(refreshToken);
        addCookie(response, "access_token", newAccessToken, 30 * 60);
        return Result.ok(null);
    }

    @PostMapping("/info")
    public Result<Map<String, Object>> info(Authentication authentication) {
        Long userId = (Long) authentication.getDetails();
        String account = (String) authentication.getPrincipal();
        User user = authService.getUserById(userId);
        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("account", account);
        data.put("username", user != null ? user.getUsername() : "");
        return Result.ok(data);
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletResponse response) {
        removeCookie(response, "access_token");
        removeCookie(response, "refresh_token");
        return Result.ok(null);
    }

    private void addCookie(HttpServletResponse response, String name, String value, int maxAge) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .path("/")
                .httpOnly(true)
                .maxAge(Duration.ofSeconds(maxAge))
                .sameSite("Lax")
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private void removeCookie(HttpServletResponse response, String name) {
        ResponseCookie cookie = ResponseCookie.from(name, "")
                .path("/")
                .httpOnly(true)
                .maxAge(Duration.ZERO)
                .sameSite("Lax")
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private String getTokenFromCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}

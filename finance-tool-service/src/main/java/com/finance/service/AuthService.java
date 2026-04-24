package com.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.finance.common.JwtUtil;
import com.finance.dao.mapper.UserMapper;
import com.finance.model.entity.User;
import io.jsonwebtoken.Claims;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public void register(String username, String account, String password) {
        User existing = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getAccount, account));
        if (existing != null) {
            throw new RuntimeException("账号已存在");
        }
        User user = new User();
        user.setUsername(username);
        user.setAccount(account);
        user.setPassword(passwordEncoder.encode(password));
        userMapper.insert(user);
    }

    public Map<String, Object> login(String account, String password, boolean remember) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getAccount, account));
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new RuntimeException("账号或密码错误");
        }

        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getAccount());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId(), user.getAccount(), remember);

        Map<String, Object> result = new HashMap<>();
        result.put("userId", user.getId());
        result.put("username", user.getUsername());
        result.put("account", user.getAccount());
        result.put("access_token", accessToken);
        result.put("refresh_token", refreshToken);
        return result;
    }

    public User getUserById(Long userId) {
        return userMapper.selectById(userId);
    }

    public String refreshAccessToken(String refreshToken) {
        try {
            Claims claims = jwtUtil.parseToken(refreshToken);
            if (jwtUtil.isTokenExpired(claims)) {
                throw new RuntimeException("refresh_token 已过期");
            }
            Long userId = claims.get("userId", Long.class);
            String account = claims.get("account", String.class);
            return jwtUtil.generateAccessToken(userId, account);
        } catch (RuntimeException e) {
            throw new RuntimeException("无效的 refresh_token");
        }
    }
}

package com.tongtin.identity.dto;

import java.util.List;
import java.util.Map;

public record AuthResponse(
        Map<String, Object> user,
        Map<String, Object> owner,
        List<String> roles,
        Map<String, Object> tokens) {

    public static AuthResponse of(
            Map<String, Object> user,
            Map<String, Object> owner,
            List<String> roles,
            Map<String, Object> tokens) {
        return new AuthResponse(user, owner, roles, tokens);
    }
}

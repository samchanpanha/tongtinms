package com.tongtin.common.security;

import java.util.List;

public record AuthPrincipal(Long userId, Long ownerId, List<String> roles) {
}

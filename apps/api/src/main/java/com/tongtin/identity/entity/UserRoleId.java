package com.tongtin.identity.entity;

import java.io.Serializable;
import java.util.Objects;

public class UserRoleId implements Serializable {

    private Long userId;
    private String role;

    public UserRoleId() {
    }

    public UserRoleId(Long userId, String role) {
        this.userId = userId;
        this.role = role;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserRoleId other)) return false;
        return Objects.equals(userId, other.userId) && Objects.equals(role, other.role);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, role);
    }
}

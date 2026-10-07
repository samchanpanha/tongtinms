package com.tongtin.identity.repository;

import com.tongtin.identity.entity.UserRole;
import com.tongtin.identity.entity.UserRoleId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    List<UserRole> findByUserId(Long userId);

    @Query("SELECT r.role FROM UserRole r WHERE r.userId = :userId ORDER BY r.role")
    List<String> findRolesByUserId(@Param("userId") Long userId);

    @Modifying
    @Transactional
    @Query(value = "DELETE FROM user_roles WHERE user_id = :userId", nativeQuery = true)
    void deleteAllByUserId(Long userId);
}

package com.tongtin.identity.repository;

import com.tongtin.identity.entity.OwnerAccount;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OwnerAccountRepository extends JpaRepository<OwnerAccount, Long> {

    Optional<OwnerAccount> findByUserId(Long userId);
}

package com.tongtin.members.blacklist.repository;

import com.tongtin.members.blacklist.entity.MemberBlacklist;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberBlacklistRepository extends JpaRepository<MemberBlacklist, Long> {

    List<MemberBlacklist> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    Optional<MemberBlacklist> findByOwnerIdAndPhone(Long ownerId, String phone);

    Optional<MemberBlacklist> findByOwnerIdAndId(Long ownerId, Long id);

    boolean existsByOwnerIdAndPhoneAndActiveTrue(Long ownerId, String phone);
}

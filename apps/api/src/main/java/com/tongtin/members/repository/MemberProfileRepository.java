package com.tongtin.members.repository;

import com.tongtin.members.entity.MemberProfile;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberProfileRepository extends JpaRepository<MemberProfile, Long> {

    List<MemberProfile> findByOwnerIdOrderByFullNameAsc(Long ownerId);

    List<MemberProfile> findByPhone(String phone);

    Optional<MemberProfile> findByOwnerIdAndId(Long ownerId, Long id);

    boolean existsByOwnerIdAndPhone(Long ownerId, String phone);

    long countByOwnerId(Long ownerId);

    @Query("""
            SELECT m FROM MemberProfile m
            WHERE m.ownerId = :ownerId
              AND (LOWER(m.fullName) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR m.phone LIKE CONCAT('%', :q, '%'))
            ORDER BY m.fullName
            """)
    List<MemberProfile> searchByOwner(@Param("ownerId") Long ownerId, @Param("q") String q);
}

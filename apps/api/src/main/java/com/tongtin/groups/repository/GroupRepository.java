package com.tongtin.groups.repository;

import com.tongtin.groups.entity.Group;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupRepository extends JpaRepository<Group, Long> {

    List<Group> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    Optional<Group> findByOwnerIdAndId(Long ownerId, Long id);

    long countByOwnerId(Long ownerId);

    @Query("SELECT g.code FROM Group g WHERE g.ownerId = :ownerId AND g.code LIKE CONCAT(:prefix, '%')")
    List<String> findCodesByOwnerStartingWith(@Param("ownerId") Long ownerId, @Param("prefix") String prefix);
}

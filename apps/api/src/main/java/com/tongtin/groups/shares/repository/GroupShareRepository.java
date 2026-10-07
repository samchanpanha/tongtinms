package com.tongtin.groups.shares.repository;

import com.tongtin.groups.shares.entity.GroupShare;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GroupShareRepository extends JpaRepository<GroupShare, Long> {

    List<GroupShare> findByGroupIdOrderByShareNo(Long groupId);

    List<GroupShare> findByGroupIdAndMemberProfileIdIn(Long groupId, Collection<Long> memberProfileIds);

    List<GroupShare> findByMemberProfileIdIn(Collection<Long> memberProfileIds);

    Optional<GroupShare> findByGroupIdAndId(Long groupId, Long id);

    long countByGroupId(Long groupId);

    long countByGroupIdAndMemberProfileId(Long groupId, Long memberProfileId);

    @Query("SELECT COALESCE(MAX(s.shareNo), 0) FROM GroupShare s WHERE s.groupId = :groupId")
    int findMaxShareNo(@Param("groupId") Long groupId);

    void deleteByGroupIdAndId(Long groupId, Long id);
}

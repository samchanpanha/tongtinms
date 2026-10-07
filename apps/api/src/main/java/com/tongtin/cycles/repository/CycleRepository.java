package com.tongtin.cycles.repository;

import com.tongtin.cycles.entity.Cycle;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CycleRepository extends JpaRepository<Cycle, Long> {

    List<Cycle> findByGroupIdOrderByCycleNo(Long groupId);

    Optional<Cycle> findTopByGroupIdOrderByCycleNoDesc(Long groupId);

    Optional<Cycle> findByGroupIdAndCycleNo(Long groupId, int cycleNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Cycle c WHERE c.id = :id")
    Optional<Cycle> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            SELECT COALESCE(SUM(c.hostFee), 0)
            FROM Cycle c WHERE c.groupId = :groupId AND c.status = 'SETTLED'
            """)
    long sumHostFeeByGroupAndSettled(@Param("groupId") Long groupId);
}
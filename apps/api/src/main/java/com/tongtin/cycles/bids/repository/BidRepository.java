package com.tongtin.cycles.bids.repository;

import com.tongtin.cycles.bids.entity.Bid;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BidRepository extends JpaRepository<Bid, Long> {

    Optional<Bid> findByCycleIdAndShareIdAndLatestTrue(Long cycleId, Long shareId);

    List<Bid> findByCycleIdAndLatestTrue(Long cycleId);

    @Modifying
    @Query("UPDATE Bid b SET b.latest = false WHERE b.cycleId = :cycleId AND b.shareId = :shareId")
    void markNonLatest(@Param("cycleId") Long cycleId, @Param("shareId") Long shareId);
}
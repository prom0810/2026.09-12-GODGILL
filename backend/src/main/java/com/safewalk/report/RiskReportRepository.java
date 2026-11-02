package com.safewalk.report;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RiskReportRepository extends JpaRepository<RiskReport, Long> {

    /** 반려된 제보를 제외한 커뮤니티 목록. 같은 시각에는 최신 ID가 먼저 온다. */
    Slice<RiskReport> findByStatusNotOrderByCreatedAtDescReportIdDesc(
            ReportStatus status,
            Pageable pageable
    );

    /** 본인이 작성한 제보 1건 */
    Optional<RiskReport> findByReportIdAndUser_UserId(Long reportId, Long userId);

    /**
     * 본인이 작성한 제보 1건을 쓰기 잠금과 함께 조회한다.
     * 같은 제보에 사진 추가가 동시에 들어와도 "제보당 최대 장수" 규칙이 깨지지 않게 하기 위함.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RiskReport r where r.reportId = :reportId and r.user.userId = :userId")
    Optional<RiskReport> findOwnedForUpdate(@Param("reportId") Long reportId, @Param("userId") Long userId);
}

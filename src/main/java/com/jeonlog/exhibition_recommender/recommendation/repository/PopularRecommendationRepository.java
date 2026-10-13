package com.jeonlog.exhibition_recommender.recommendation.repository;

import com.jeonlog.exhibition_recommender.exhibition.domain.Exhibition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public interface PopularRecommendationRepository extends JpaRepository<Exhibition, Long> {

    /**
     * 인기 전시 id 조회.
     * 클릭 로그와 북마크를 한 번에 JOIN하면 전시마다 (클릭 수 × 북마크 수)개의 행이 만들어져
     * COUNT(DISTINCT)로 값은 맞아도 중간 행이 크게 늘어난다.
     * 그래서 전시별로 클릭 수와 북마크 수를 각각 상관 서브쿼리로 센 뒤 점수를 계산한다.
     * (exhibition_id, 날짜) 복합 인덱스를 타므로 전시마다 범위 조회 한 번으로 끝난다.
     */
    @Query("""
        SELECT t.id
        FROM (
            SELECT e.id AS id,
                   (SELECT COUNT(ecl) FROM ExhibitionClickLog ecl
                     WHERE ecl.exhibition = e
                       AND ecl.clickedDate BETWEEN :fromDate AND :toDate) AS clicks,
                   (SELECT COUNT(b) FROM Bookmark b
                     WHERE b.exhibition = e
                       AND b.createdAt BETWEEN :fromDt AND :toDt) AS bookmarks
            FROM Exhibition e
            WHERE e.endDate >= :today
        ) t
        ORDER BY (t.clicks * :clickWeight + t.bookmarks * :bookmarkWeight) DESC,
                 t.bookmarks DESC,
                 t.clicks DESC,
                 t.id DESC
        """)
    List<Long> findTopPopularExhibitionIds(
            @Param("today") LocalDate today,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("fromDt") LocalDateTime fromDt,
            @Param("toDt") LocalDateTime toDt,
            @Param("clickWeight") double clickWeight,
            @Param("bookmarkWeight") double bookmarkWeight,
            org.springframework.data.domain.Pageable pageable
    );
}

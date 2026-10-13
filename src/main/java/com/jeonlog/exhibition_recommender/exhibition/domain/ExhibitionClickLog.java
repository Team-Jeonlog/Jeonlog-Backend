package com.jeonlog.exhibition_recommender.exhibition.domain;

import com.jeonlog.exhibition_recommender.user.domain.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "exhibition_click_logs", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "exhibition_id", "clicked_date"})
}, indexes = {
        // 전시별 기간 클릭 수 조회용 (유니크 키는 user_id가 앞이라 전시 기준 범위 조회에 쓸 수 없다)
        @Index(name = "idx_click_exhibition_date", columnList = "exhibition_id, clicked_date")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ExhibitionClickLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "clicked_date", nullable = false)
    private LocalDate clickedDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exhibition_id", nullable = false)
    private Exhibition exhibition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}


package com.jeonlog.exhibition_recommender.bookmark.domain;

import com.jeonlog.exhibition_recommender.exhibition.domain.Exhibition;
import com.jeonlog.exhibition_recommender.user.domain.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "bookmarks",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_bookmark_user_exhibition",
                columnNames = {"user_id", "exhibition_id"}
        ),
        indexes = {
                // 전시별 기간 북마크 수 조회용 (exhibition_id 단독 조회도 이 인덱스가 담당)
                @Index(name = "idx_bookmark_exhibition_created", columnList = "exhibition_id, created_at"),
                @Index(name = "idx_bookmark_user", columnList = "user_id")
        }
)
public class Bookmark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exhibition_id", nullable = false)
    private Exhibition exhibition;

    // 알림 여부 (전시 시작/변경 등 푸시 알림용)
    @Column(nullable = false)
    private boolean notifyEnabled = false;

    // 생성 시각 (DB 기본값 + 애플리케이션 보장)
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // ✅ 엔티티가 처음 저장되기 전에 createdAt 자동 세팅
    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }

    @Builder
    public Bookmark(User user, Exhibition exhibition) {
        this.user = user;
        this.exhibition = exhibition;
    }

    public void updateNotify(boolean enabled) {
        this.notifyEnabled = enabled;
    }
}
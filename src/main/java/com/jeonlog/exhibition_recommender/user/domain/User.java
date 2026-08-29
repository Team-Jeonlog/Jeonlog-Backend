package com.jeonlog.exhibition_recommender.user.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Formula;

import java.time.LocalDateTime;

@Entity//엔티티
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"oauth_provider", "oauth_id"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String email; // Apple은 null 가능

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private OauthProvider oauthProvider;

    @Column(nullable = false)
    private String oauthId;

    @Column(unique = true, length = 30)
    private String nickname; // 온보딩 후 입력

    private LocalDateTime createdAt;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    private Integer birthYear;

    @Column(length = 1000)
    private String introduction;

    private String profileImageUrl;

    @Column(length = 20)
    private String signature;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Role role;

    @Column(nullable = false)
    private Integer moderationStrike;

    private LocalDateTime suspendedUntil;

    @Column(nullable = false)
    private Boolean permanentlyBanned;

    @Formula("(SELECT COUNT(*) FROM follow f WHERE f.following_id = id)")
    private int followerCount;

    @Formula("(SELECT COUNT(*) FROM follow f WHERE f.follower_id = id)")
    private int followingCount;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.signature == null) this.signature = "jeonlog";
        if (this.role == null) this.role = Role.USER;
        if (this.moderationStrike == null) this.moderationStrike = 0;
        if (this.permanentlyBanned == null) this.permanentlyBanned = false;
    }

    public void update(String name) {
        this.name = name;
    }

    public void completeOnboarding(
            Gender gender,
            Integer birthYear,
            String nickname
    ) {
        this.gender = gender;
        this.birthYear = birthYear;
        this.nickname = nickname;
    }

    public void updateProfile(
            Gender gender,
            Integer birthYear,
            String nickname,
            String introduction,
            String profileImageUrl,
            String signature
    ) {
        if (gender != null) this.gender = gender;
        if (birthYear != null) this.birthYear = birthYear;
        if (nickname != null && !nickname.isBlank()) this.nickname = nickname;
        if (introduction != null) this.introduction = introduction;
        if (profileImageUrl != null) this.profileImageUrl = profileImageUrl;
        if (signature != null && !signature.isBlank()) this.signature = signature;
    }

    public int getModerationStrikeValue() {
        return moderationStrike == null ? 0 : moderationStrike;
    }

    public void increaseModerationStrike() {
        if (this.moderationStrike == null) {
            this.moderationStrike = 0;
        }
        this.moderationStrike += 1;
    }

    public void warn() {
        increaseModerationStrike();
    }

    public void suspendForDays(int days) {
        increaseModerationStrike();
        this.suspendedUntil = LocalDateTime.now().plusDays(days);
    }

    public void banPermanently() {
        increaseModerationStrike();
        this.permanentlyBanned = true;
    }
}

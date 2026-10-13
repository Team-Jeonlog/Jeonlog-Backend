package com.jeonlog.exhibition_recommender.recommendation.repository;

import com.jeonlog.exhibition_recommender.bookmark.domain.Bookmark;
import com.jeonlog.exhibition_recommender.exhibition.domain.Exhibition;
import com.jeonlog.exhibition_recommender.exhibition.domain.ExhibitionClickLog;
import com.jeonlog.exhibition_recommender.exhibition.domain.ExhibitionTheme;
import com.jeonlog.exhibition_recommender.exhibition.domain.GenreType;
import com.jeonlog.exhibition_recommender.exhibition.domain.Venue;
import com.jeonlog.exhibition_recommender.exhibition.domain.VenueType;
import com.jeonlog.exhibition_recommender.support.MySqlContainerTestSupport;
import com.jeonlog.exhibition_recommender.user.domain.OauthProvider;
import com.jeonlog.exhibition_recommender.user.domain.Role;
import com.jeonlog.exhibition_recommender.user.domain.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 인기 전시 집계 쿼리가 클릭 수와 북마크 수를 각각 올바르게 세는지 고정한다.
 * 클릭 로그와 북마크를 한 번에 JOIN하는 방식은 전시마다 (클릭 수 × 북마크 수)개의 행이 만들어지는데,
 * 서브쿼리로 바꾸면서 점수 계산과 정렬 기준(점수 → 북마크 → 클릭 → id 내림차순)이 달라지지 않았는지 확인한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PopularRecommendationRepositoryTest extends MySqlContainerTestSupport {

    private static final LocalDate TODAY = LocalDate.now();

    @Autowired
    private PopularRecommendationRepository popularRepository;
    @Autowired
    private EntityManager em;

    private Venue venue;
    private final List<User> users = new ArrayList<>();

    @Test
    void 클릭과_북마크를_각각_세어_점수순으로_정렬한다() {
        venue = persistVenue();
        for (int i = 0; i < 6; i++) users.add(persistUser());

        // 클릭 3 · 북마크 2 → 점수 5 (JOIN 곱으로 세면 6행이 되어 점수가 부풀어 1위가 되는 전시)
        Exhibition a = persistExhibition("A", TODAY.plusDays(5));
        clicks(a, 3, TODAY);
        bookmarks(a, 2);
        // 클릭 6 · 북마크 0 → 점수 6
        Exhibition b = persistExhibition("B", TODAY.plusDays(5));
        clicks(b, 6, TODAY);
        // 클릭 1 · 북마크 4 → 점수 5, A와 동점이면 북마크가 많은 쪽이 먼저
        Exhibition c = persistExhibition("C", TODAY.plusDays(5));
        clicks(c, 1, TODAY);
        bookmarks(c, 4);
        em.flush();
        em.clear();

        List<Long> ids = findTop();

        assertThat(ids).startsWith(b.getId(), c.getId(), a.getId());
    }

    @Test
    void 종료된_전시와_집계_기간_밖_클릭은_점수에_반영하지_않는다() {
        venue = persistVenue();
        for (int i = 0; i < 6; i++) users.add(persistUser());

        // 어제 끝난 전시는 클릭이 아무리 많아도 제외
        Exhibition ended = persistExhibition("ended", TODAY.minusDays(1));
        clicks(ended, 6, TODAY);
        // 집계 기간(30일) 밖 클릭 6개는 점수 0, 기간 안 클릭 1개인 전시가 먼저
        Exhibition old = persistExhibition("old", TODAY.plusDays(5));
        clicks(old, 6, TODAY.minusDays(40));
        Exhibition recent = persistExhibition("recent", TODAY.plusDays(5));
        clicks(recent, 1, TODAY);
        em.flush();
        em.clear();

        List<Long> ids = findTop();

        assertThat(ids).doesNotContain(ended.getId());
        assertThat(ids).startsWith(recent.getId(), old.getId());
    }

    @Test
    void 점수와_북마크와_클릭이_모두_같으면_id가_큰_전시가_먼저다() {
        venue = persistVenue();
        for (int i = 0; i < 6; i++) users.add(persistUser());

        Exhibition first = persistExhibition("first", TODAY.plusDays(5));
        Exhibition second = persistExhibition("second", TODAY.plusDays(5));
        clicks(first, 2, TODAY);
        clicks(second, 2, TODAY);
        em.flush();
        em.clear();

        assertThat(findTop()).startsWith(second.getId(), first.getId());
    }

    private List<Long> findTop() {
        LocalDateTime now = LocalDateTime.now();
        return popularRepository.findTopPopularExhibitionIds(
                TODAY, TODAY.minusDays(30), TODAY, now.minusDays(30), now.plusMinutes(1),
                1.0, 1.0, PageRequest.of(0, 10));
    }

    private void clicks(Exhibition exhibition, int count, LocalDate date) {
        for (int i = 0; i < count; i++) {
            em.persist(ExhibitionClickLog.builder()
                    .user(users.get(i)).exhibition(exhibition).clickedDate(date).build());
        }
    }

    private void bookmarks(Exhibition exhibition, int count) {
        for (int i = 0; i < count; i++) {
            em.persist(Bookmark.builder().user(users.get(i)).exhibition(exhibition).build());
        }
    }

    private Venue persistVenue() {
        Venue v = Venue.builder().name("테스트 전시장").type(VenueType.GALLERY).build();
        em.persist(v);
        return v;
    }

    private User persistUser() {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        User u = User.builder()
                .email("popular-" + unique + "@example.com")
                .name("popular-name-" + unique)
                .oauthProvider(OauthProvider.GOOGLE)
                .oauthId("popular-oauth-" + unique)
                .nickname("popular-nick-" + unique)
                .role(Role.USER)
                .build();
        em.persist(u);
        return u;
    }

    private Exhibition persistExhibition(String title, LocalDate endDate) {
        Exhibition e = Exhibition.builder()
                .title(title).description("설명").location("서울")
                .posterUrl("https://example.com/poster.jpg")
                .startDate(TODAY.minusDays(10)).endDate(endDate)
                .price(0).isFree(true)
                .exhibitionTheme(ExhibitionTheme.CASUAL_VISIT).genre(GenreType.MODERN_ART)
                .contact("02-0000-0000").website("https://example.com").viewingTime("10:00-18:00")
                .personalizedPosterUrl("https://example.com/personalized.jpg")
                .generalRecommendationsPosterUrl("https://example.com/general.jpg")
                .venue(venue).build();
        em.persist(e);
        return e;
    }
}

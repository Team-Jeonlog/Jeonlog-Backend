package com.jeonlog.exhibition_recommender.exhibition.controller;

import com.jeonlog.exhibition_recommender.auth.config.JwtTokenProvider;
import com.jeonlog.exhibition_recommender.exhibition.domain.Exhibition;
import com.jeonlog.exhibition_recommender.exhibition.domain.ExhibitionTheme;
import com.jeonlog.exhibition_recommender.exhibition.domain.GenreType;
import com.jeonlog.exhibition_recommender.exhibition.domain.Venue;
import com.jeonlog.exhibition_recommender.exhibition.domain.VenueType;
import com.jeonlog.exhibition_recommender.exhibition.repository.ExhibitionClickLogRepository;
import com.jeonlog.exhibition_recommender.exhibition.repository.ExhibitionRepository;
import com.jeonlog.exhibition_recommender.exhibition.repository.VenueRepository;
import com.jeonlog.exhibition_recommender.support.MySqlContainerTestSupport;
import com.jeonlog.exhibition_recommender.user.domain.OauthProvider;
import com.jeonlog.exhibition_recommender.user.domain.Role;
import com.jeonlog.exhibition_recommender.user.domain.User;
import com.jeonlog.exhibition_recommender.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ExhibitionClickLogSecurityIntegrationTest extends MySqlContainerTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private ExhibitionRepository exhibitionRepository;

    @Autowired
    private ExhibitionClickLogRepository exhibitionClickLogRepository;

    private User user;
    private Exhibition exhibition;

    @BeforeEach
    void setUp() {
        exhibitionClickLogRepository.deleteAll();
        exhibitionRepository.deleteAll();
        venueRepository.deleteAll();
        userRepository.deleteAll();

        user = userRepository.save(User.builder()
                .email("clicklog-user@example.com")
                .name("clicklog-user")
                .oauthProvider(OauthProvider.GOOGLE)
                .oauthId("clicklog-user")
                .role(Role.USER)
                .build());

        Venue venue = venueRepository.save(Venue.builder()
                .name("클릭로그 테스트 전시장")
                .type(VenueType.GALLERY)
                .build());

        exhibition = exhibitionRepository.save(Exhibition.builder()
                .title("클릭로그 테스트 전시")
                .description("설명")
                .location("서울")
                .posterUrl("https://example.com/poster.jpg")
                .startDate(LocalDate.now().minusDays(1))
                .endDate(LocalDate.now().plusDays(1))
                .price(0)
                .isFree(true)
                .exhibitionTheme(ExhibitionTheme.CASUAL_VISIT)
                .genre(GenreType.MODERN_ART)
                .contact("02-0000-0000")
                .website("https://example.com")
                .viewingTime("10:00-18:00")
                .personalizedPosterUrl("https://example.com/personalized.jpg")
                .generalRecommendationsPosterUrl("https://example.com/general.jpg")
                .venue(venue)
                .build());
    }

    @Test
    void saveClickLog_withoutAuthorization_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/exhibitions/{id}/click", exhibition.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"error\":\"Unauthorized\"}"));
    }

    @Test
    void saveClickLog_withValidToken_returnsCreated() throws Exception {
        String accessToken = jwtTokenProvider.createAccessToken(user);

        mockMvc.perform(post("/api/exhibitions/{id}/click", exhibition.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.logId").isNumber())
                .andExpect(jsonPath("$.data.exhibitionId").value(exhibition.getId()))
                .andExpect(jsonPath("$.data.userId").value(user.getId()))
                .andExpect(jsonPath("$.data.clickedAt").isString());

        assertThat(exhibitionClickLogRepository.count()).isEqualTo(1L);
    }
}

package com.jeonlog.exhibition_recommender.webhook;

import com.jeonlog.exhibition_recommender.support.MySqlContainerTestSupport;
import com.jeonlog.exhibition_recommender.webhook.domain.WebhookDelivery;
import com.jeonlog.exhibition_recommender.webhook.domain.WebhookDeliveryStatus;
import com.jeonlog.exhibition_recommender.webhook.domain.WebhookEventType;
import com.jeonlog.exhibition_recommender.webhook.repository.WebhookDeliveryRepository;
import com.jeonlog.exhibition_recommender.webhook.scheduler.WebhookReplayScheduler;
import com.jeonlog.exhibition_recommender.webhook.service.DiscordWebhookClient;
import com.jeonlog.exhibition_recommender.webhook.service.WebhookDeliveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

/**
 * 오토스케일(ASG min 1 / max 4) 환경에서 인스턴스가 늘어났을 때
 * 웹훅 재전송 배치가 중복 실행되는지를 실제로 재현하고 측정한다.
 *
 * 측정 대상을 웹훅으로 고른 이유:
 * 전시 종료 알림은 notifications.dedup_key UNIQUE 제약이 최종 방어선으로 남아 있어
 * 중복 실행이 나도 알림 행은 두 번 생기지 않는다. 반면 웹훅 재전송은
 * dedup 키도 행 잠금도 없고 dispatch()가 외부 HTTP 호출이라 되돌릴 수 없다.
 * 즉 분산 락이 유일한 방어선인 자리다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ShedLockDuplicateExecutionEvidenceTest extends MySqlContainerTestSupport {

    /** 재전송 대기 중인 웹훅 건수 */
    private static final int PENDING_COUNT = 30;
    /** 동시에 배치를 도는 인스턴스 수 (ASG 스케일아웃 상황) */
    private static final int INSTANCES = 4;
    /** 외부 HTTP 호출 지연 — 실제 Discord 왕복을 모사해 경합 구간을 현실적으로 만든다 */
    private static final long DISPATCH_LATENCY_MS = 30L;

    @Autowired
    private WebhookDeliveryService webhookDeliveryService;

    @Autowired
    private WebhookReplayScheduler webhookReplayScheduler;

    @Autowired
    private WebhookDeliveryRepository webhookDeliveryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private DiscordWebhookClient discordWebhookClient;

    private AtomicInteger actualSendCount;

    @BeforeEach
    void setUp() {
        // ShedLock 테이블은 JPA 엔티티가 아니라 ddl-auto가 만들어주지 않는다.
        // 운영 스키마는 docs/shedlock-ddl.sql이며 아래 정의는 그것과 동일해야 한다.
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS shedlock (
                    name       VARCHAR(64)   NOT NULL,
                    lock_until TIMESTAMP(3)  NOT NULL,
                    locked_at  TIMESTAMP(3)  NOT NULL,
                    locked_by  VARCHAR(255)  NOT NULL,
                    PRIMARY KEY (name)
                )
                """);
        jdbcTemplate.update("DELETE FROM shedlock");
        webhookDeliveryRepository.deleteAll();

        actualSendCount = new AtomicInteger();
        Answer<Void> counting = invocation -> {
            actualSendCount.incrementAndGet();
            Thread.sleep(DISPATCH_LATENCY_MS);
            return null;
        };
        doAnswer(counting).when(discordWebhookClient).sendReportWebhook(anyLong(), anyString(), anyString());
        doAnswer(counting).when(discordWebhookClient).sendBlockWebhook(anyLong(), anyString(), anyString());

        seedPendingDeliveries();
    }

    private void seedPendingDeliveries() {
        LocalDateTime dueInThePast = LocalDateTime.now().minusMinutes(5);
        for (int i = 0; i < PENDING_COUNT; i++) {
            webhookDeliveryRepository.save(WebhookDelivery.builder()
                    .eventType(WebhookEventType.REPORT_CREATED)
                    .targetId((long) i)
                    .webhookUrl("https://example.invalid/webhook")
                    .payload("{\"seq\":" + i + "}")
                    .status(WebhookDeliveryStatus.PENDING_RETRY)
                    .attemptCount(1)
                    .nextRetryAt(dueInThePast)
                    .build());
        }
    }

    private void runOnAllInstancesSimultaneously(Runnable batch) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(INSTANCES);
        CountDownLatch startGun = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(INSTANCES);

        for (int i = 0; i < INSTANCES; i++) {
            pool.submit(() -> {
                try {
                    startGun.await();
                    batch.run();
                } catch (Exception ignored) {
                    // 락 획득 실패·경합 예외는 이 실험의 관심사가 아니다 — 발송 횟수만 센다
                } finally {
                    finished.countDown();
                }
            });
        }

        startGun.countDown();
        finished.await(120, TimeUnit.SECONDS);
        pool.shutdownNow();
    }

    @Test
    void 분산락이_없으면_인스턴스_수만큼_웹훅이_중복_발송된다() throws Exception {
        // 락을 거치지 않고 서비스를 직접 호출한다 = @SchedulerLock이 없던 시절의 배치
        runOnAllInstancesSimultaneously(() -> webhookDeliveryService.replayPendingDeliveries());

        int sent = actualSendCount.get();
        System.out.printf("%n[락 없음] 대기 %d건 · 인스턴스 %d대 → 실제 발송 %d건 (중복 %d건)%n",
                PENDING_COUNT, INSTANCES, sent, sent - PENDING_COUNT);

        assertThat(sent).isGreaterThan(PENDING_COUNT);
    }

    @Test
    void ShedLock이_있으면_한_인스턴스만_실행돼_중복이_사라진다() throws Exception {
        // @SchedulerLock이 걸린 스케줄러 빈을 프록시 경유로 호출한다 = 현재 운영 코드
        runOnAllInstancesSimultaneously(() -> webhookReplayScheduler.replay());

        int sent = actualSendCount.get();
        System.out.printf("%n[ShedLock 적용] 대기 %d건 · 인스턴스 %d대 → 실제 발송 %d건 (중복 %d건)%n",
                PENDING_COUNT, INSTANCES, sent, sent - PENDING_COUNT);

        assertThat(sent).isEqualTo(PENDING_COUNT);
    }
}

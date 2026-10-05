package dev.parakhatdin.ledger.webhook;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, Long> {

    Optional<WebhookEvent> findByEventId(String eventId);

    /**
     * Stores the event unless one with the same {@code event_id} already exists.
     * {@code ON CONFLICT DO NOTHING} makes the check and the insert one atomic statement, so two parallel
     * deliveries of the same event cannot both get in.
     *
     * @return 1 if stored, 0 if it was a duplicate delivery
     */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO webhook_events (event_id, type, payload, status, attempts, received_at)
            VALUES (:eventId, :type, :payload, 'RECEIVED', 0, :receivedAt)
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("eventId") String eventId,
                       @Param("type") String type,
                       @Param("payload") String payload,
                       @Param("receivedAt") Instant receivedAt);

    /**
     * Locks a batch of pending events for this worker. {@code FOR UPDATE SKIP LOCKED} lets several
     * application instances poll the same table: each row is handed to exactly one of them, and nobody
     * waits on another worker's locks.
     */
    @Query(value = """
            SELECT * FROM webhook_events
            WHERE status = 'RECEIVED'
            ORDER BY id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<WebhookEvent> lockPendingBatch(@Param("limit") int limit);
}

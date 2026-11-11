package com.ticket.infrastructure.adapter.out.redis;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisSeatCacheService {

    private final StringRedisTemplate stringRedisTemplate;

    private DefaultRedisScript<List> reserveSeatScript;
    private DefaultRedisScript<List> releaseSeatScript;

    @PostConstruct
    public void initScripts() {
        reserveSeatScript = new DefaultRedisScript<>();
        reserveSeatScript.setLocation(new ClassPathResource("scripts/reserve_seat.lua"));
        reserveSeatScript.setResultType(List.class);

        releaseSeatScript = new DefaultRedisScript<>();
        releaseSeatScript.setLocation(new ClassPathResource("scripts/release_seat.lua"));
        releaseSeatScript.setResultType(List.class);
    }

    public static String getSeatHashKey(String eventId) {
        return "{event:" + eventId + "}:seats";
    }

    public static String getExpirationZSetKey(String eventId) {
        return "{event:" + eventId + "}:expirations";
    }

    /**
     * Tải trước toàn bộ sơ đồ ghế lên Redis Hash trước giờ mở bán (Pre-warming)
     */
    public void preWarmSeats(String eventId, Map<String, String> seatStatusMap) {
        String key = getSeatHashKey(eventId);
        stringRedisTemplate.opsForHash().putAll(key, seatStatusMap);
        log.info("Pre-warmed {} seats for eventId: {}", seatStatusMap.size(), eventId);
    }

    /**
     * Thực thi Lua Script giữ chỗ nguyên tử
     */
    public ReservationResult reserveSeats(String eventId, String userId, long expireTimestamp, List<String> seatIds) {
        List<String> keys = Arrays.asList(getSeatHashKey(eventId), getExpirationZSetKey(eventId));

        List<String> args = new ArrayList<>();
        args.add(userId);
        args.add(String.valueOf(expireTimestamp));
        args.addAll(seatIds);

        List<?> result = stringRedisTemplate.execute(reserveSeatScript, keys, args.toArray());

        if (result != null && !result.isEmpty()) {
            Number status = (Number) result.get(0);
            if (status.intValue() == 1) {
                return new ReservationResult(true, null);
            } else {
                String conflictingSeatId = result.size() > 1 ? String.valueOf(result.get(1)) : null;
                return new ReservationResult(false, conflictingSeatId);
            }
        }

        return new ReservationResult(false, "REDIS_EXECUTION_FAILED");
    }

    /**
     * Giải phóng ghế an toàn khi có lỗi hoặc hết hạn
     */
    public void releaseSeats(String eventId, String userId, List<String> seatIds) {
        List<String> keys = Arrays.asList(getSeatHashKey(eventId), getExpirationZSetKey(eventId));

        List<String> args = new ArrayList<>();
        args.add(userId);
        args.addAll(seatIds);

        stringRedisTemplate.execute(releaseSeatScript, keys, args.toArray());
        log.info("Released seats {} for userId: {} in eventId: {}", seatIds, userId, eventId);
    }

    /**
     * Lấy trạng thái hiện tại của một ghế
     */
    public String getSeatStatus(String eventId, String seatId) {
        Object val = stringRedisTemplate.opsForHash().get(getSeatHashKey(eventId), seatId);
        return val != null ? val.toString() : null;
    }

    /**
     * Lấy toàn bộ trạng thái ghế của sự kiện
     */
    public Map<Object, Object> getAllSeatStatuses(String eventId) {
        return stringRedisTemplate.opsForHash().entries(getSeatHashKey(eventId));
    }

    @Getter
    @RequiredArgsConstructor
    public static class ReservationResult {
        private final boolean success;
        private final String conflictingSeatId;
    }
}

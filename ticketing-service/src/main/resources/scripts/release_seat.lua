-- ============================================================================
-- FLASHTICKET ATOMIC SEAT RELEASE SCRIPT (release_seat.lua)
-- Giải phóng ghế an toàn (Saga Compensating Transaction)
-- ============================================================================
-- KEYS[1]: event seat map hash   (vd: "{event:101}:seats")
-- KEYS[2]: event expiration zset  (vd: "{event:101}:expirations")
-- ARGV[1]: userId (Người đang giữ ghế)
-- ARGV[2...N]: Danh sách ID ghế cần giải phóng

local seatHashKey = KEYS[1]
local expirationZSetKey = KEYS[2]
local userId = ARGV[1]
local expectedHeldValue = 'HELD:' .. userId

for i = 2, #ARGV do
    local seatId = ARGV[i]
    local currentStatus = redis.call('HGET', seatHashKey, seatId)

    -- Chỉ giải phóng nếu ghế đang được giữ bởi chính userId này
    -- Tuyệt đối không ghi đè nếu ghế đã chuyển sang SOLD hoặc bị giữ bởi người khác
    if currentStatus == expectedHeldValue then
        redis.call('HSET', seatHashKey, seatId, 'AVAILABLE')
        redis.call('ZREM', expirationZSetKey, seatId)
    end
end

return {1, 'RELEASED'}

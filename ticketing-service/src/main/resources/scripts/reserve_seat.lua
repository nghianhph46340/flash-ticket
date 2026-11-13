-- ============================================================================
-- FLASHTICKET ATOMIC SEAT RESERVATION SCRIPT (reserve_seat.lua)
-- Đảm bảo tính nguyên tử tuyệt đối (Zero Overselling, All-or-Nothing)
-- ============================================================================
-- KEYS[1]: event seat map hash  (vd: "{event:101}:seats")
-- KEYS[2]: event expiration zset (vd: "{event:101}:expirations")
-- ARGV[1]: userId
-- ARGV[2]: expireTimestamp (Unix timestamp tính bằng giây, vd: now + 600)
-- ARGV[3...N]: Danh sách ID ghế yêu cầu giữ

local seatHashKey = KEYS[1]
local expirationZSetKey = KEYS[2]
local userId = ARGV[1]
local expireTime = tonumber(ARGV[2])

-- 1. KIỂM TRA ĐIỀU KIỆN (All-or-Nothing Validation)
-- Duyệt qua toàn bộ danh sách ghế yêu cầu. Nếu có ít nhất 1 ghế không ở trạng thái AVAILABLE thì từ chối ngay.
-- Lưu ý: nil (ghế chưa được set trong Redis) được coi là AVAILABLE — không yêu cầu pre-warm.
for i = 3, #ARGV do
    local seatId = ARGV[i]
    local currentStatus = redis.call('HGET', seatHashKey, seatId)
    
    -- nil = chưa ai đặt bao giờ → AVAILABLE
    -- 'AVAILABLE' = đã được set tường minh → AVAILABLE
    -- Bất kỳ giá trị khác (HELD:xxx, SOLD) → từ chối
    if currentStatus ~= false and currentStatus ~= 'AVAILABLE' then
        -- Ghế không khả dụng (đã bị HELD bởi người khác, hoặc SOLD)
        return {0, seatId}
    end
end

-- 2. KHÓA GHẾ NGUYÊN TỬ ĐỒNG LOẠT
-- Khi toàn bộ ghế đều khả dụng, thực hiện khóa tất cả ghế cho userId này
for i = 3, #ARGV do
    local seatId = ARGV[i]
    redis.call('HSET', seatHashKey, seatId, 'HELD:' .. userId)
    redis.call('ZADD', expirationZSetKey, expireTime, seatId)
end

-- Trả về thành công
return {1, 'SUCCESS'}

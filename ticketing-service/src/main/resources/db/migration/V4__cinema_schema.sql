-- ============================================================
-- V4__cinema_schema.sql
-- Mở rộng schema sang Cinema Domain:
--   - Thêm enums: seat_type, age_rating, showtime_status, screen_format
--   - Thêm bảng: movies, cinemas, auditoriums, showtimes,
--                cinema_seats, concessions, order_concessions
--   - Mở rộng bảng seats và orders
--   - Index tối ưu
--   - Seed dữ liệu mẫu
-- ============================================================

-- ============ ENUMS ============

DO $$ BEGIN
    CREATE TYPE seat_type AS ENUM ('STANDARD', 'VIP', 'SWEETBOX', 'BLOCKED');
EXCEPTION WHEN duplicate_object THEN null;
END $$;

DO $$ BEGIN
    CREATE TYPE age_rating AS ENUM ('P', 'K', 'T13', 'T16', 'T18');
EXCEPTION WHEN duplicate_object THEN null;
END $$;

DO $$ BEGIN
    CREATE TYPE showtime_status AS ENUM ('DRAFT', 'PUBLISHED', 'CANCELLED', 'ARCHIVED');
EXCEPTION WHEN duplicate_object THEN null;
END $$;

DO $$ BEGIN
    CREATE TYPE screen_format AS ENUM ('STANDARD_2D', 'STANDARD_3D', 'IMAX', 'DOLBY_ATMOS', 'FOUR_DX');
EXCEPTION WHEN duplicate_object THEN null;
END $$;

-- ============ BẢNG MỚI ============

-- Bảng movies (Phim)
CREATE TABLE IF NOT EXISTS movies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL UNIQUE,
    duration_minutes INT NOT NULL CHECK (duration_minutes > 0),
    age_rating age_rating NOT NULL DEFAULT 'P',
    director VARCHAR(150),
    cast_members TEXT,
    synopsis TEXT,
    poster_url VARCHAR(500),
    trailer_url VARCHAR(500),
    release_date DATE,
    end_date DATE,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Bảng cinemas (Cụm rạp - Chi nhánh)
CREATE TABLE IF NOT EXISTS cinemas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    address TEXT NOT NULL,
    city VARCHAR(100) NOT NULL,
    hotline VARCHAR(20),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Bảng auditoriums (Phòng chiếu trực thuộc rạp)
CREATE TABLE IF NOT EXISTS auditoriums (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    cinema_id UUID NOT NULL REFERENCES cinemas(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    screen_format screen_format NOT NULL DEFAULT 'STANDARD_2D',
    total_seats INT NOT NULL CHECK (total_seats > 0),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Bảng showtimes (Suất chiếu)
CREATE TABLE IF NOT EXISTS showtimes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    movie_id UUID NOT NULL REFERENCES movies(id) ON DELETE RESTRICT,
    auditorium_id UUID NOT NULL REFERENCES auditoriums(id) ON DELETE RESTRICT,
    start_time TIMESTAMP WITH TIME ZONE NOT NULL,
    end_time TIMESTAMP WITH TIME ZONE NOT NULL,
    base_price NUMERIC(12, 2) NOT NULL CHECK (base_price >= 0),
    status showtime_status NOT NULL DEFAULT 'DRAFT',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_showtime_order CHECK (end_time > start_time)
);

-- Bảng cinema_seats (Ghế rạp phim, gắn với auditorium)
CREATE TABLE IF NOT EXISTS cinema_seats (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    auditorium_id UUID NOT NULL REFERENCES auditoriums(id) ON DELETE CASCADE,
    seat_type seat_type NOT NULL DEFAULT 'STANDARD',
    row_label VARCHAR(5) NOT NULL,
    seat_number INT NOT NULL CHECK (seat_number > 0),
    pair_seat_id UUID REFERENCES cinema_seats(id) ON DELETE SET NULL,
    coordinate_x DOUBLE PRECISION,
    coordinate_y DOUBLE PRECISION,
    is_active BOOLEAN DEFAULT TRUE,
    CONSTRAINT uq_cinema_seat UNIQUE (auditorium_id, row_label, seat_number)
);

-- Bảng concessions (Danh mục bắp nước F&B)
CREATE TABLE IF NOT EXISTS concessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(150) NOT NULL,
    description TEXT,
    price NUMERIC(10, 2) NOT NULL CHECK (price >= 0),
    image_url VARCHAR(500),
    category VARCHAR(50) NOT NULL DEFAULT 'COMBO',
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Bảng order_concessions (Bắp nước trong đơn hàng)
CREATE TABLE IF NOT EXISTS order_concessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    concession_id UUID NOT NULL REFERENCES concessions(id) ON DELETE RESTRICT,
    quantity INT NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(10, 2) NOT NULL,
    CONSTRAINT uq_order_concession UNIQUE (order_id, concession_id)
);

-- ============ MỞ RỘNG BẢNG HIỆN TẠI ============

-- Thêm cột seat_type và pair_seat_id vào bảng seats hiện tại
ALTER TABLE seats ADD COLUMN IF NOT EXISTS seat_type seat_type NOT NULL DEFAULT 'STANDARD';
ALTER TABLE seats ADD COLUMN IF NOT EXISTS pair_seat_id UUID REFERENCES seats(id) ON DELETE SET NULL;

-- Thêm cột showtime_id và movie_id vào orders để liên kết với suất chiếu
ALTER TABLE orders ADD COLUMN IF NOT EXISTS showtime_id UUID REFERENCES showtimes(id) ON DELETE RESTRICT;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS movie_id UUID REFERENCES movies(id) ON DELETE RESTRICT;

-- ============ INDEX TỐI ƯU ============

-- Index cho showtimes
CREATE INDEX IF NOT EXISTS idx_showtimes_movie ON showtimes (movie_id, status);
CREATE INDEX IF NOT EXISTS idx_showtimes_auditorium ON showtimes (auditorium_id, start_time);
CREATE INDEX IF NOT EXISTS idx_showtimes_start_time ON showtimes (start_time, status);

-- Index cho cinema_seats
CREATE INDEX IF NOT EXISTS idx_cinema_seats_auditorium ON cinema_seats (auditorium_id, seat_type);

-- Index cho concessions
CREATE INDEX IF NOT EXISTS idx_concessions_active ON concessions (is_active, category);

-- ============ SEED DATA CHO DEMO ============

-- 1 cụm rạp
INSERT INTO cinemas (id, name, address, city, hotline) VALUES
    ('00000000-0000-0000-0001-000000000001', 'FlashCinema Galaxy', '116 Nguyễn Du, Quận 1', 'Hồ Chí Minh', '1900 1234')
ON CONFLICT DO NOTHING;

-- 2 phòng chiếu
INSERT INTO auditoriums (id, cinema_id, name, screen_format, total_seats) VALUES
    ('00000000-0000-0000-0002-000000000001', '00000000-0000-0000-0001-000000000001', 'Phòng 1 - Standard', 'STANDARD_2D', 80),
    ('00000000-0000-0000-0002-000000000002', '00000000-0000-0000-0001-000000000001', 'Phòng 2 - IMAX', 'IMAX', 60)
ON CONFLICT DO NOTHING;

-- 3 bộ phim
INSERT INTO movies (id, title, slug, duration_minutes, age_rating, director, synopsis, poster_url, trailer_url, release_date, is_active) VALUES
    ('00000000-0000-0000-0003-000000000001', 'Avengers: Doomsday', 'avengers-doomsday', 148, 'T13', 'Russo Brothers',
     'Các siêu anh hùng Marvel tập hợp để đối mặt với mối đe dọa vũ trụ lớn nhất từ trước đến nay.',
     'https://picsum.photos/seed/avengers/400/600', 'https://www.youtube.com/watch?v=dQw4w9WgXcQ', '2026-05-01', true),
    ('00000000-0000-0000-0003-000000000002', 'Lật Mặt 8: Vòng Tay Nắng', 'lat-mat-8', 125, 'T16', 'Lý Hải',
     'Tiếp nối thành công của series Lật Mặt, phần 8 tiếp tục câu chuyện về gia đình và những bí mật.',
     'https://picsum.photos/seed/latmat/400/600', 'https://www.youtube.com/watch?v=dQw4w9WgXcQ', '2026-04-30', true),
    ('00000000-0000-0000-0003-000000000003', 'Minecraft: The Movie', 'minecraft-movie', 101, 'P', 'Jared Hess',
     'Cuộc phiêu lưu kỳ thú trong thế giới khối vuông của Steve và những người bạn.',
     'https://picsum.photos/seed/minecraft/400/600', 'https://www.youtube.com/watch?v=dQw4w9WgXcQ', '2026-04-04', true)
ON CONFLICT DO NOTHING;

-- 5 suất chiếu (hôm nay và ngày mai)
INSERT INTO showtimes (id, movie_id, auditorium_id, start_time, end_time, base_price, status) VALUES
    ('00000000-0000-0000-0004-000000000001',
     '00000000-0000-0000-0003-000000000001', '00000000-0000-0000-0002-000000000001',
     NOW() + INTERVAL '1 hour', NOW() + INTERVAL '3 hours 28 minutes', 85000, 'PUBLISHED'),
    ('00000000-0000-0000-0004-000000000002',
     '00000000-0000-0000-0003-000000000001', '00000000-0000-0000-0002-000000000002',
     NOW() + INTERVAL '4 hours', NOW() + INTERVAL '6 hours 28 minutes', 120000, 'PUBLISHED'),
    ('00000000-0000-0000-0004-000000000003',
     '00000000-0000-0000-0003-000000000002', '00000000-0000-0000-0002-000000000001',
     NOW() + INTERVAL '7 hours', NOW() + INTERVAL '9 hours 5 minutes', 85000, 'PUBLISHED'),
    ('00000000-0000-0000-0004-000000000004',
     '00000000-0000-0000-0003-000000000003', '00000000-0000-0000-0002-000000000001',
     NOW() + INTERVAL '2 hours', NOW() + INTERVAL '3 hours 41 minutes', 75000, 'PUBLISHED'),
    ('00000000-0000-0000-0004-000000000005',
     '00000000-0000-0000-0003-000000000001', '00000000-0000-0000-0002-000000000001',
     NOW() + INTERVAL '25 hours', NOW() + INTERVAL '27 hours 28 minutes', 85000, 'PUBLISHED')
ON CONFLICT DO NOTHING;

-- F&B Concessions
INSERT INTO concessions (id, name, description, price, category, is_active) VALUES
    ('00000000-0000-0000-0005-000000000001', 'Combo 1 Bắp + 1 Nước',
     'Bắp rang bơ cỡ vừa + Pepsi/7Up cỡ vừa', 65000, 'COMBO', true),
    ('00000000-0000-0000-0005-000000000002', 'Combo Couple: 2 Bắp + 2 Nước',
     'Bắp rang bơ cỡ lớn + Pepsi/7Up cỡ lớn x2', 120000, 'COMBO', true),
    ('00000000-0000-0000-0005-000000000003', 'Bắp Phô Mai Cỡ Lớn',
     'Bắp rang phô mai giòn, cỡ lớn', 55000, 'POPCORN', true),
    ('00000000-0000-0000-0005-000000000004', 'Bắp Caramel Cỡ Lớn',
     'Bắp rang caramel ngọt, cỡ lớn', 55000, 'POPCORN', true),
    ('00000000-0000-0000-0005-000000000005', 'Nước Ngọt Cỡ Lớn',
     'Pepsi/7Up/Mirinda cỡ lớn', 35000, 'DRINK', true)
ON CONFLICT DO NOTHING;

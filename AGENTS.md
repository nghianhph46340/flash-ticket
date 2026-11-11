# Repository Instructions (auto-env)

## 1. Môi trường NixOS & Runtimes (Bắt buộc)

- **Hệ điều hành**: NixOS với Flakes & Direnv (`.envrc: use flake`).
- **Tuyệt đối KHÔNG cài đặt toàn cục**: Không dùng `npm i -g`, `pip install`, `apt`, `yum`...
- **Runtimes có sẵn qua Flake devShell**:
  - **Java**: OpenJDK 17 (`java`, `javac`)
  - **Node / Frontend**: Node.js 24, `pnpm` (10.x), `tsc` (5.9.x), `tsx`, `nodemon`, `prettier`, `eslint`
  - **.NET**: .NET SDK 8 (`dotnet`), `DOTNET_ROOT` và `LD_LIBRARY_PATH` (Avalonia) đã export
  - **Rust**: `cargo`, `rustc` (1.92.x), `rust-analyzer` (`RUST_SRC_PATH` trỏ tới stdlib source)
  - **Python**: Python 3.12 (`python3`), `pip`, `huggingface-hub`
  - **Công cụ khác**: `ripgrep`, `wireguard-tools`, `cmake`, `ninja`, `gdb`
- **Containers**: Dùng **Podman** (5.8.x), KHÔNG có `docker`. Chạy compose qua `podman compose` (wrapper cho `docker-compose`).

## 2. Quy tắc Git & Workspace

- **Workspace Monorepo**: `/home/huunghia/Documents/workspace/auto-env` là root repo git quản lý dev shell chung.
- **Gitignore Quirk**: `.gitignore` có rule `/*` (chỉ whitelist các file dev shell). Khi thêm file mới ở root muốn commit, cần whitelist trong `.gitignore` (`!AGENTS.md`).

## 3. Dự án FlashTicket (`flash-tickets/`)

Hệ thống đặt vé & flash sale chịu tải cao (Zero Overselling, Sub-20ms latency) tối ưu cho VPS 3–4GB RAM (~860MB–1.1GB RAM tổng).

- **Source of Truth**: Mọi chi tiết thiết kế nằm tại `flash-tickets/KE_HOACH_VA_THIET_KE_KIEN_TRUC_flashticket.md` (schema 12 bảng, Lua script, NATS deduplication, Vue 3, roadmap).
- **Phân tách trách nhiệm hạ tầng**:
  - **Redis 7.2**: Khóa & trừ vé nguyên tử in-memory bằng Lua script (`reserve_seat.lua`), TTL 600s.
  - **NATS JetStream** (`nats:latest -js`): Streaming sự kiện bất đồng bộ, deduplication qua header `Nats-Msg-Id`.
  - **PostgreSQL 16**: Lưu trữ quan hệ lâu dài, ghi bất đồng bộ (Write-Behind) từ NATS worker để tránh lock contention.
  - **Backend API**: Spring Boot 3 đóng gói GraalVM Native Image (AOT binary, RAM ~40–60MB).
  - **Frontend**: Vue 3 + TypeScript + Tailwind + STOMP WebSocket (interactive SVG seat map).
- **Local Dev Services**: Dùng `podman compose` khởi chạy Postgres (5432), Redis (6379), NATS JetStream (4222, 8222), MinIO (9000, 9001).
- **Bảo mật**: `flash-tickets/vps_1.pem` là SSH private key của VPS (chmod 600). TUYỆT ĐỐI không in nội dung ra chat, không commit, không sửa/xóa.

## 4. Nguyên tắc Kỹ thuật

- Giao tiếp và giải thích: **Tiếng Việt**.
- Luôn kiểm chứng qua terminal trước khi xác nhận hoàn thành (test, compile, podman).
- Không đoán mò cấu trúc: Luôn tham chiếu file spec và code lân cận.

## 5. Máy chủ hiện tại truy cập bằng lệnh sau

- Phân quyền nếu chưa có: `sudo chmod 600 vps_1.pem`, nếu hỏi mật khẩu thì dùng: `huunghia1710`
- Vào bằng cách: ssh -i `vps_1.pem`(file trong dự án) nghianh@3.25.83.72, lúc nào vào thì hãy bảo lại tôi

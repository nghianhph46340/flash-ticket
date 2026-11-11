# ==============================================================================
# FlashTicket - Dockerfile cho Frontend Vue 3 + Nginx Alpine
# ==============================================================================

# Stage 1: Build static assets bằng Node.js 24 Alpine
FROM node:24-alpine AS build-stage
WORKDIR /app

# Kích hoạt corepack để dùng pnpm
RUN corepack enable && corepack prepare pnpm@10.9.0 --activate

# Copy package.json và pnpm-lock.yaml để cache layers
COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile --config.supply-chain.safety-checks.minimumReleaseAge=0

# Copy mã nguồn và biên dịch production build
COPY . .
RUN pnpm build

# Stage 2: Serve bằng Nginx Alpine siêu nhẹ (~20MB)
FROM nginx:alpine
WORKDIR /usr/share/nginx/html

# Xóa các file mặc định của Nginx
RUN rm -rf ./*

# Copy file tĩnh đã build từ Stage 1
COPY --from=build-stage /app/dist .

# Copy cấu hình Nginx tối ưu hỗ trợ SPA, Gzip và WebSocket reverse proxy
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]

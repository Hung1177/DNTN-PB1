# ==============================================================================
# Stage 1: Build Vue 3 + TypeScript Application
# ==============================================================================
FROM node:20-alpine AS builder
WORKDIR /app

COPY package*.json ./
RUN if [ -f package-lock.json ]; then npm ci; elif [ -f package.json ]; then npm install; else mkdir -p dist && echo "<h1>O2O Food Pickup Web Ready</h1>" > dist/index.html; fi

COPY . .
RUN if [ -f package.json ]; then npm run build; fi

# ==============================================================================
# Stage 2: Serve with Nginx & Reverse Proxy /api to Backend
# ==============================================================================
FROM nginx:1.27-alpine
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/Asia/Ho_Chi_Minh /etc/localtime && \
    echo "Asia/Ho_Chi_Minh" > /etc/timezone

ENV TZ=Asia/Ho_Chi_Minh

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=builder /app/dist /usr/share/nginx/html

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]

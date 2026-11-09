# ==========================================
# Stage 1: Build Angular application
# ==========================================
FROM node:22-alpine AS builder

WORKDIR /app

# Copy dependency manifests
COPY package.json package-lock.json* ./

# Install npm dependencies (npm install is more forgiving with lock file drifts)
RUN npm install --no-audit --no-fund

# Copy application source code
COPY . .

# Build production bundle
RUN npm run build -- --configuration production

# ==========================================
# Stage 2: Serve with Nginx Alpine
# ==========================================
FROM nginx:alpine AS runner

# Remove default nginx html files
RUN rm -rf /usr/share/nginx/html/*

# Copy build output from builder stage
COPY --from=builder /app/dist/frontend/browser /usr/share/nginx/html

# Copy custom Nginx configuration
COPY nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost/ || exit 1

CMD ["nginx", "-g", "daemon off;"]

# -------------------------------------------------------------
# AniStream HUB - Production Dockerfile
# Optimized, secure, lightweight multi-stage Node.js container
# -------------------------------------------------------------

FROM node:22-alpine AS base

# Install curl for healthcheck
RUN apk add --no-cache curl

# Set working directory
WORKDIR /app

# Set environment
ENV NODE_ENV=production
ENV PORT=5050

# Copy package descriptors
COPY package*.json ./

# Install dependencies cleanly
RUN npm ci --omit=dev --ignore-scripts && npm cache clean --force

# Copy application source
COPY server.js ./
COPY public ./public

# Use non-root node user for security
RUN chown -R node:node /app
USER node

# Expose default application port
EXPOSE 5050

# Container Healthcheck
HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
  CMD curl -f http://localhost:5050/api/sample-streams || exit 1

# Start Server
CMD ["node", "server.js"]

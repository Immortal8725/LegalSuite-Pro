# syntax=docker/dockerfile:1
FROM node:22-alpine AS build
WORKDIR /src
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY . .
# The API address is baked into the Next.js rewrites at build time.
ARG NEXT_PUBLIC_API_URL=http://api:18081
ARG NEXT_PUBLIC_PUBLIC_SITE_ROOT=
ENV NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL \
    NEXT_PUBLIC_PUBLIC_SITE_ROOT=$NEXT_PUBLIC_PUBLIC_SITE_ROOT \
    NEXT_TELEMETRY_DISABLED=1
ENV NEXT_OUTPUT=standalone
RUN npm run build

FROM node:22-alpine
WORKDIR /app
ENV NODE_ENV=production \
    PORT=3000 \
    HOSTNAME=0.0.0.0 \
    NEXT_TELEMETRY_DISABLED=1
COPY --from=build --chown=node:node /src/.next/standalone ./
COPY --from=build --chown=node:node /src/.next/static ./.next/static
COPY --from=build --chown=node:node /src/public ./public
USER node
EXPOSE 3000
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD wget -q -O /dev/null "http://127.0.0.1:${PORT}/login" || exit 1
CMD ["node", "server.js"]

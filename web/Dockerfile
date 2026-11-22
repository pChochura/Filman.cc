# ---------- Build Stage ----------
FROM node:20-alpine AS build
WORKDIR /app

ARG TMDB_API_KEY=""
ARG STREAM_API_BASE_URL=""
ENV VITE_TMDB_API_KEY=$TMDB_API_KEY \
    VITE_STREAM_API_CONFIGURED=${STREAM_API_BASE_URL:+true}

COPY package.json package-lock.json ./
RUN npm install

COPY . .
RUN npm run build

# ---------- Runtime Stage ----------
FROM node:20-alpine AS runtime
WORKDIR /app

ENV NODE_ENV=production \
    PORT=3000 \
    HOST=0.0.0.0

COPY --from=build /app/dist ./dist
COPY --from=build /app/server ./server
COPY --from=build /app/server.js ./server.js
COPY --from=build /app/package.json ./package.json
COPY --from=build /app/node_modules ./node_modules

EXPOSE 3000

CMD ["node", "server.js"]

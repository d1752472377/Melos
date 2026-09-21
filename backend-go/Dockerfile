# ---- build stage ----
FROM golang:1.26-alpine AS build
WORKDIR /src
ENV CGO_ENABLED=0 GOOS=linux
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN go build -trimpath -ldflags "-s -w" -o /out/melos-backend .

# ---- runtime stage ----
FROM alpine:3.20
RUN apk add --no-cache ca-certificates tzdata
RUN adduser -D -u 10001 melos
WORKDIR /app
COPY --from=build /out/melos-backend /usr/local/bin/melos-backend
RUN mkdir -p /app/data/apk
RUN chown -R melos:melos /app
USER melos

ENV PORT=8787
ENV HOST=0.0.0.0
ENV DB_URL=file:/app/data/music.db
ENV APK_DIR=/app/data/apk

EXPOSE 8787
VOLUME ["/app/data"]
CMD ["melos-backend"]

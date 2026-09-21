// Command melos-backend is the Go/Gin rewrite of the Melos backend.
//
// It keeps the exact HTTP contract of the original Node/Fastify service so the
// Android client and music-admin work unchanged, while storing everything in a
// local SQLite database.
package main

import (
	"context"
	"errors"
	"log"
	"net"
	"net/http"
	"os"
	"os/signal"
	"strconv"
	"syscall"
	"time"

	"melos-backend/internal/config"
	"melos-backend/internal/httpapi"
	"melos-backend/internal/store"
)

func main() {
	cfg := config.Load()

	st, err := store.Open(cfg.DBURL)
	if err != nil {
		log.Fatalf("[melos-backend] 打开数据库失败: %v", err)
	}
	defer func() { _ = st.Close() }()

	ctx := context.Background()

	// Ensure the built-in administrator exists (admin / ADMIN_INITIAL_PASSWORD).
	if created, err := st.EnsureAdminUser(ctx, "admin", config.AdminInitialPassword()); err != nil {
		log.Fatalf("[melos-backend] 初始化管理员失败: %v", err)
	} else if created {
		log.Printf("[melos-backend] 已创建内置管理员账号 admin")
	}

	if err := os.MkdirAll(cfg.APKDir, 0o755); err != nil {
		log.Fatalf("[melos-backend] 创建 APK 目录失败: %v", err)
	}

	server := httpapi.NewServer(cfg, st)
	engine := server.Router()

	// Startup cleanup: expired sessions + play events older than 180 days.
	now := time.Now().UnixMilli()
	if n, err := st.PruneExpiredSessions(ctx, now); err != nil {
		log.Printf("[melos-backend] 清理过期会话失败: %v", err)
	} else if n > 0 {
		log.Printf("[melos-backend] 启动清理：过期会话 %d 条", n)
	}
	if n, err := st.PruneOldPlayEvents(ctx, 180, now); err != nil {
		log.Printf("[melos-backend] 清理旧听歌明细失败: %v", err)
	} else if n > 0 {
		log.Printf("[melos-backend] 启动清理：旧听歌明细 %d 条", n)
	}

	addr := net.JoinHostPort(cfg.Host, strconv.Itoa(cfg.Port))
	httpSrv := &http.Server{
		Addr:              addr,
		Handler:           engine,
		ReadHeaderTimeout: 10 * time.Second,
	}

	go func() {
		log.Printf("[melos-backend] listening on %s", addr)
		if err := httpSrv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			log.Fatalf("[melos-backend] 监听失败: %v", err)
		}
	}()

	quit := make(chan os.Signal, 1)
	signal.Notify(quit, os.Interrupt, syscall.SIGTERM)
	<-quit
	log.Printf("[melos-backend] 正在关闭...")

	shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := httpSrv.Shutdown(shutdownCtx); err != nil {
		log.Printf("[melos-backend] 关闭超时: %v", err)
	}
}

package config

import (
	"bufio"
	"os"
	"strconv"
	"strings"
)

// Config mirrors backend/src/lib/config.js.
type Config struct {
	Port int
	Host string

	// DBURL accepts a local SQLite path or a "file:" URL.
	// Remote libsql:// / http:// endpoints are intentionally not supported.
	DBURL string

	TrustProxy        bool
	RateLimitDisabled bool

	SessionTTLDays     int
	MaxSessionsPerUser int

	MailServiceURL  string
	MailAdminAuth   string
	MailFromAddress string
	MailFromName    string

	// APK distribution
	APKDir   string
	APKMaxMB int

	// APKPublicBase 是大带宽镜像的对外基址（例如 http://1.2.3.4/apk）。
	//
	// 非空时，版本接口会把 `apkUrl` 换成「镜像直链」，并额外下发
	// `apk_url_fallback`（本机相对路径）供客户端兜底。
	// 背景：自建 ECS 只有 3M 上行（实测满速约 430KB/s），8.5MB 的安装包要 20 秒以上，
	// 弱网下经常断；镜像机带宽大得多。于是「版本信息仍由本机给（JSON 很小），
	// 安装包走镜像（大文件）」——两者解耦，换镜像不需要发客户端版本。
	APKPublicBase string

	// GitHub Release & CI integration
	ReleaseToken   string
	GitHubRepo     string
	GitHubToken    string
	GitHubProxyURL string
}

func intEnv(name string, fallback, min int) int {
	if raw := strings.TrimSpace(os.Getenv(name)); raw != "" {
		if n, err := strconv.Atoi(raw); err == nil {
			if n < min {
				return min
			}
			return n
		}
	}
	return fallback
}

func boolEnv(name string) bool {
	return os.Getenv(name) == "1"
}

// Load reads configuration from environment variables (after applying an
// optional .env file in the working directory, mirroring dotenv behaviour).
func Load() *Config {
	loadDotEnv(".env")

	cfg := &Config{
		Port:               intEnv("PORT", 8787, 1),
		Host:               envOr("HOST", "0.0.0.0"),
		DBURL:              envOr("DB_URL", "file:data/music.db"),
		TrustProxy:         boolEnv("TRUST_PROXY"),
		RateLimitDisabled:  boolEnv("RATE_LIMIT_DISABLED"),
		SessionTTLDays:     intEnv("SESSION_TTL_DAYS", 90, 1),
		MaxSessionsPerUser: intEnv("MAX_SESSIONS_PER_USER", 10, 1),
		MailServiceURL:     envOr("MAIL_SERVICE_URL", "https://mail.dovislab.com"),
		MailAdminAuth:      envOr("MAIL_ADMIN_AUTH", ""),
		MailFromAddress:    envOr("MAIL_FROM_ADDRESS", "melos@dovislab.com"),
		MailFromName:       envOr("MAIL_FROM_NAME", "Melos 音乐"),
		APKDir:             envOr("APK_DIR", "data/apk"),
		APKMaxMB:           intEnv("APK_MAX_MB", 200, 1),
		APKPublicBase:      strings.TrimSpace(os.Getenv("APK_PUBLIC_BASE")),
		ReleaseToken:       strings.TrimSpace(os.Getenv("RELEASE_TOKEN")),
		GitHubRepo:         envOr("GITHUB_REPO", "d1752472377/Melos"),
		GitHubToken:        strings.TrimSpace(os.Getenv("GITHUB_TOKEN")),
		GitHubProxyURL:     strings.TrimSpace(os.Getenv("GITHUB_PROXY_URL")),
	}
	return cfg
}

// AdminInitialPassword returns the seeded admin password (default 12346).
func AdminInitialPassword() string {
	return envOr("ADMIN_INITIAL_PASSWORD", "12346")
}

func envOr(name, fallback string) string {
	if v := os.Getenv(name); v != "" {
		return v
	}
	return fallback
}

// APKMaxBytes is the maximum accepted APK upload size.
func (c *Config) APKMaxBytes() int64 { return int64(c.APKMaxMB) << 20 }

// SessionTTLMillis converts the configured TTL to milliseconds.
func (c *Config) SessionTTLMillis() int64 {
	return int64(c.SessionTTLDays) * 24 * 60 * 60 * 1000
}

// loadDotEnv sets variables from a simple KEY=VALUE .env file. Existing
// environment variables always win, matching dotenv's default behaviour.
func loadDotEnv(path string) {
	f, err := os.Open(path)
	if err != nil {
		return
	}
	defer f.Close()

	scanner := bufio.NewScanner(f)
	for scanner.Scan() {
		line := strings.TrimSpace(scanner.Text())
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		eq := strings.IndexByte(line, '=')
		if eq <= 0 {
			continue
		}
		key := strings.TrimSpace(line[:eq])
		val := strings.TrimSpace(line[eq+1:])
		val = strings.Trim(val, `"'`)
		if _, exists := os.LookupEnv(key); !exists {
			_ = os.Setenv(key, val)
		}
	}
}

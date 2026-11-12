package httpapi

import (
	"bytes"
	"crypto/rand"
	"encoding/base64"
	"encoding/hex"
	"image"
	"image/color"
	"image/png"
	"math/big"
	mrand "math/rand"
	"net/http"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/gin-gonic/gin"

	"melos-backend/internal/store"
)

// 问题反馈：客户端提交（带图形验证码），管理端查看与回复。

const (
	feedbackMinContent   = 10
	feedbackMaxContent   = 2000
	feedbackMaxImages    = 3
	feedbackMaxImageSize = 400 * 1024 // 单张 base64 长度上限
	captchaTTL           = 5 * time.Minute
)

// 与客户端保持一致的问题类型
var feedbackTypes = map[string]bool{
	"playback": true,
	"lyrics":   true,
	"library":  true,
	"account":  true,
	"ui":       true,
	"feature":  true,
	"other":    true,
}

type captchaEntry struct {
	code      string
	expiresAt time.Time
}

type captchaStore struct {
	mu      sync.Mutex
	entries map[string]captchaEntry
}

func newCaptchaStore() *captchaStore {
	return &captchaStore{entries: make(map[string]captchaEntry)}
}

func (s *captchaStore) put(id, code string, ttl time.Duration) {
	s.mu.Lock()
	defer s.mu.Unlock()
	now := time.Now()
	// 顺手清掉过期项，验证码量很小，全量扫一遍即可
	for k, v := range s.entries {
		if now.After(v.expiresAt) {
			delete(s.entries, k)
		}
	}
	s.entries[id] = captchaEntry{code: code, expiresAt: now.Add(ttl)}
}

// take 取出并删除（一次性），返回是否有效。
func (s *captchaStore) take(id string) (string, bool) {
	s.mu.Lock()
	defer s.mu.Unlock()
	entry, ok := s.entries[id]
	if !ok {
		return "", false
	}
	delete(s.entries, id)
	if time.Now().After(entry.expiresAt) {
		return "", false
	}
	return entry.code, true
}

func (s *Server) registerFeedback(r *gin.Engine) {
	r.GET("/api/feedback/captcha", s.handleFeedbackCaptcha)

	auth := s.requireUser()
	r.POST("/api/feedback", auth, s.handleCreateFeedback)
	r.GET("/api/feedback/mine", auth, s.handleMyFeedback)

	admin := s.requireAdmin()
	r.GET("/api/admin/feedback", admin, s.handleAdminListFeedback)
	r.PATCH("/api/admin/feedback/:id", admin, s.handleAdminReplyFeedback)
}

func (s *Server) handleFeedbackCaptcha(c *gin.Context) {
	code := randomDigits(5)
	id := randomHex(16)
	pngBytes, err := renderCaptcha(code)
	if err != nil {
		fail(c, http.StatusInternalServerError, "验证码生成失败")
		return
	}
	s.captchas.put(id, code, captchaTTL)
	ok(c, gin.H{
		"id":    id,
		"image": base64.StdEncoding.EncodeToString(pngBytes),
	})
}

func (s *Server) handleCreateFeedback(c *gin.Context) {
	body, okBody := decodeBody(c)
	if !okBody {
		return
	}

	typeName := strings.TrimSpace(asString(body["type"]))
	if !feedbackTypes[typeName] {
		fail(c, http.StatusBadRequest, "请选择问题类型")
		return
	}

	content := strings.TrimSpace(asString(body["content"]))
	if runeLen(content) < feedbackMinContent {
		fail(c, http.StatusBadRequest, "问题描述至少 10 个字")
		return
	}
	if runeLen(content) > feedbackMaxContent {
		fail(c, http.StatusBadRequest, "问题描述不能超过 2000 字")
		return
	}

	// 验证码一次性校验
	captchaID := strings.TrimSpace(asString(body["captchaId"]))
	answer := strings.TrimSpace(asString(body["captcha"]))
	want, valid := s.captchas.take(captchaID)
	if !valid || !strings.EqualFold(want, answer) {
		fail(c, http.StatusBadRequest, "验证码不正确或已过期")
		return
	}

	rawImages, _ := body["images"].([]any)
	if len(rawImages) > feedbackMaxImages {
		fail(c, http.StatusBadRequest, "最多上传 3 张图片")
		return
	}
	images := make([]string, 0, len(rawImages))
	total := 0
	for _, raw := range rawImages {
		data := asString(raw)
		data = strings.TrimPrefix(data, "data:image/jpeg;base64,")
		data = strings.TrimPrefix(data, "data:image/png;base64,")
		data = strings.TrimPrefix(data, "data:image/webp;base64,")
		if data == "" {
			continue
		}
		if len(data) > feedbackMaxImageSize {
			fail(c, http.StatusBadRequest, "单张图片过大，请压缩后重试")
			return
		}
		total += len(data)
		images = append(images, data)
	}
	if total > feedbackMaxImages*feedbackMaxImageSize {
		fail(c, http.StatusBadRequest, "图片总大小超出限制")
		return
	}

	withLogs, _ := body["withLogs"].(bool)
	user := currentUser(c)
	if user == nil {
		fail(c, http.StatusUnauthorized, "请先登录")
		return
	}

	id, err := s.store.CreateFeedback(c.Request.Context(), user.ID, store.Feedback{
		Type:       typeName,
		Content:    content,
		Images:     images,
		WithLogs:   withLogs,
		AppVersion: strings.TrimSpace(asString(body["appVersion"])),
		Device:     strings.TrimSpace(asString(body["device"])),
	}, store.NowMs())
	if err != nil {
		fail(c, http.StatusInternalServerError, "提交失败，请稍后再试")
		return
	}
	ok(c, gin.H{"id": id})
}

func (s *Server) handleMyFeedback(c *gin.Context) {
	user := currentUser(c)
	if user == nil {
		fail(c, http.StatusUnauthorized, "请先登录")
		return
	}
	list, err := s.store.ListFeedbackByUser(c.Request.Context(), user.ID, 50)
	if err != nil {
		fail(c, http.StatusInternalServerError, "读取反馈失败")
		return
	}
	// 列表不需要回传图片，避免响应过大
	for i := range list {
		list[i].Images = []string{}
	}
	ok(c, list)
}

func (s *Server) handleAdminListFeedback(c *gin.Context) {
	status := strings.TrimSpace(c.Query("status"))
	limit, _ := strconv.Atoi(c.DefaultQuery("limit", "50"))
	offset, _ := strconv.Atoi(c.DefaultQuery("offset", "0"))
	list, total, err := s.store.ListAllFeedback(c.Request.Context(), status, limit, offset)
	if err != nil {
		fail(c, http.StatusInternalServerError, "读取反馈失败")
		return
	}
	ok(c, gin.H{"total": total, "items": list})
}

func (s *Server) handleAdminReplyFeedback(c *gin.Context) {
	id, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil || id <= 0 {
		fail(c, http.StatusBadRequest, "反馈不存在")
		return
	}
	body, okBody := decodeBody(c)
	if !okBody {
		return
	}
	status := strings.TrimSpace(asString(body["status"]))
	switch status {
	case "pending", "processing", "resolved":
	default:
		fail(c, http.StatusBadRequest, "状态不合法")
		return
	}
	reply := strings.TrimSpace(asString(body["reply"]))
	if runeLen(reply) > 1000 {
		fail(c, http.StatusBadRequest, "回复不能超过 1000 字")
		return
	}
	if err := s.store.ReplyFeedback(c.Request.Context(), id, status, reply, store.NowMs()); err != nil {
		fail(c, http.StatusNotFound, "反馈不存在")
		return
	}
	ok(c, gin.H{"ok": true})
}

// ---------------- 验证码图片 ----------------

// 5x7 点阵数字，用于零依赖地画验证码
var digitFont = map[rune][7]string{
	'0': {"01110", "10001", "10011", "10101", "11001", "10001", "01110"},
	'1': {"00100", "01100", "00100", "00100", "00100", "00100", "01110"},
	'2': {"01110", "10001", "00001", "00010", "00100", "01000", "11111"},
	'3': {"11111", "00010", "00100", "00010", "00001", "10001", "01110"},
	'4': {"00010", "00110", "01010", "10010", "11111", "00010", "00010"},
	'5': {"11111", "10000", "11110", "00001", "00001", "10001", "01110"},
	'6': {"00110", "01000", "10000", "11110", "10001", "10001", "01110"},
	'7': {"11111", "00001", "00010", "00100", "01000", "01000", "01000"},
	'8': {"01110", "10001", "10001", "01110", "10001", "10001", "01110"},
	'9': {"01110", "10001", "10001", "01111", "00001", "00010", "01100"},
}

func renderCaptcha(code string) ([]byte, error) {
	const scale = 4
	const width, height = 150, 50

	img := image.NewRGBA(image.Rect(0, 0, width, height))
	bg := color.RGBA{0xEE, 0xF3, 0xF8, 0xFF}
	for y := 0; y < height; y++ {
		for x := 0; x < width; x++ {
			img.Set(x, y, bg)
		}
	}

	// 噪点
	for i := 0; i < 220; i++ {
		x := mrand.Intn(width)
		y := mrand.Intn(height)
		img.Set(x, y, color.RGBA{
			R: uint8(170 + mrand.Intn(60)),
			G: uint8(175 + mrand.Intn(55)),
			B: uint8(185 + mrand.Intn(50)),
			A: 255,
		})
	}
	// 干扰线
	for i := 0; i < 3; i++ {
		drawLine(img,
			mrand.Intn(width), mrand.Intn(height),
			mrand.Intn(width), mrand.Intn(height),
			color.RGBA{uint8(120 + mrand.Intn(80)), uint8(130 + mrand.Intn(70)), uint8(150 + mrand.Intn(60)), 255},
		)
	}

	x := 12
	for _, ch := range code {
		pattern, ok := digitFont[ch]
		if !ok {
			continue
		}
		dy := mrand.Intn(6) - 3
		for row := 0; row < 7; row++ {
			for col := 0; col < 5; col++ {
				if pattern[row][col] != '1' {
					continue
				}
				for sy := 0; sy < scale; sy++ {
					for sx := 0; sx < scale; sx++ {
						px := x + col*scale + sx
						py := 9 + dy + row*scale + sy
						if px >= 0 && px < width && py >= 0 && py < height {
							img.Set(px, py, color.RGBA{0x2B, 0x3A, 0x4D, 0xFF})
						}
					}
				}
			}
		}
		x += 5*scale + 7
	}

	var buf bytes.Buffer
	if err := png.Encode(&buf, img); err != nil {
		return nil, err
	}
	return buf.Bytes(), nil
}

func drawLine(img *image.RGBA, x1, y1, x2, y2 int, col color.RGBA) {
	dx := abs(x2 - x1)
	dy := -abs(y2 - y1)
	sx, sy := 1, 1
	if x1 > x2 {
		sx = -1
	}
	if y1 > y2 {
		sy = -1
	}
	err := dx + dy
	for {
		img.Set(x1, y1, col)
		if x1 == x2 && y1 == y2 {
			return
		}
		e2 := 2 * err
		if e2 >= dy {
			err += dy
			x1 += sx
		}
		if e2 <= dx {
			err += dx
			y1 += sy
		}
	}
}

func abs(v int) int {
	if v < 0 {
		return -v
	}
	return v
}

func randomDigits(n int) string {
	var sb strings.Builder
	for i := 0; i < n; i++ {
		num, err := rand.Int(rand.Reader, big.NewInt(10))
		if err != nil {
			sb.WriteByte('0')
			continue
		}
		sb.WriteByte(byte('0' + num.Int64()))
	}
	return sb.String()
}

func randomHex(n int) string {
	buf := make([]byte, n)
	if _, err := rand.Read(buf); err != nil {
		return strconv.FormatInt(time.Now().UnixNano(), 16)
	}
	return hex.EncodeToString(buf)
}

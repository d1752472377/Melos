// Package mail sends verification-code emails through the self-hosted mail
// service (mirrors lib/mail.js).
package mail

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"

	"melos-backend/internal/config"
)

// Client talks to the Cloudflare-hosted mail service.
type Client struct {
	cfg  *config.Config
	http *http.Client
}

// New builds a mail client with an 8s request timeout.
func New(cfg *config.Config) *Client {
	return &Client{cfg: cfg, http: &http.Client{Timeout: 8 * time.Second}}
}

func sceneText(scene string) string {
	switch scene {
	case "register":
		return "注册"
	case "bind":
		return "绑定/更换邮箱"
	case "unbind":
		return "解绑邮箱"
	default:
		return "登录"
	}
}

// SendVerifyCode delivers a 6-digit code email.
func (c *Client) SendVerifyCode(ctx context.Context, toMail, code, scene string) error {
	serviceURL := strings.TrimRight(c.cfg.MailServiceURL, "/")
	if serviceURL == "" {
		serviceURL = "https://mail.dovislab.com"
	}
	sceneName := sceneText(scene)
	subject := fmt.Sprintf("【Melos 音乐】%s验证码：%s", sceneName, code)

	html := strings.NewReplacer(
		"{{SCENE}}", sceneName,
		"{{CODE}}", code,
	).Replace(htmlTemplate)

	payload := map[string]any{
		"from_name": c.cfg.MailFromName,
		"from_mail": c.cfg.MailFromAddress,
		"to_mail":   toMail,
		"to_name":   "Melos 用户",
		"subject":   subject,
		"content":   html,
		"is_html":   true,
	}
	body, err := json.Marshal(payload)
	if err != nil {
		return err
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodPost, serviceURL+"/admin/send_mail", bytes.NewReader(body))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("x-admin-auth", c.cfg.MailAdminAuth)

	res, err := c.http.Do(req)
	if err != nil {
		if ctx.Err() != nil || strings.Contains(err.Error(), "deadline exceeded") {
			return fmt.Errorf("邮件服务响应超时，请稍后重试")
		}
		return err
	}
	defer res.Body.Close()

	if res.StatusCode < 200 || res.StatusCode >= 300 {
		errText, _ := io.ReadAll(io.LimitReader(res.Body, 4096))
		msg := strings.TrimSpace(string(errText))
		if msg == "" {
			msg = res.Status
		}
		return fmt.Errorf("邮件发送失败 (%d): %s", res.StatusCode, msg)
	}
	return nil
}

const htmlTemplate = `<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <style>
    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background-color: #f6f5f2; margin: 0; padding: 30px; }
    .card { max-width: 480px; margin: 0 auto; background: #ffffff; border-radius: 16px; padding: 32px; box-shadow: 0 4px 20px rgba(0,0,0,0.05); }
    .brand { font-size: 20px; font-weight: 700; color: #1c1917; margin-bottom: 20px; display: flex; align-items: center; }
    .brand span { color: #5352ed; margin-right: 8px; }
    .title { font-size: 15px; color: #44403c; line-height: 1.6; margin-bottom: 24px; }
    .code-box { background: #f5f3ff; border: 1px dashed #7b61ff; border-radius: 12px; padding: 18px; text-align: center; margin: 24px 0; }
    .code { font-size: 32px; font-weight: 800; letter-spacing: 6px; color: #5352ed; font-family: Menlo, Monaco, monospace; }
    .tip { font-size: 13px; color: #78716c; line-height: 1.5; }
    .footer { margin-top: 32px; padding-top: 16px; border-top: 1px solid #f5f5f4; font-size: 11px; color: #a8a29e; text-align: center; }
  </style>
</head>
<body>
  <div class="card">
    <div class="brand"><span>Melos</span> 音乐</div>
    <div class="title">您好！您正在申请<strong>{{SCENE}}</strong> Melos 音乐账号，请在客户端输入下方验证码完成验证：</div>
    <div class="code-box">
      <div class="code">{{CODE}}</div>
    </div>
    <div class="tip">
      · 验证码有效期为<strong>5 分钟</strong>，过期后请重新获取。<br>
      · 如非本人操作，请忽略此邮件，您的账号状态不会受到影响。
    </div>
    <div class="footer">
      本邮件由 Melos 音乐安全系统自动发出，请勿直接回复。
    </div>
  </div>
</body>
</html>
`

package store

import (
	"context"
	"database/sql"
	"encoding/json"
	"fmt"
	"time"
)

// Feedback 是客户端「问题反馈」的一条记录。
//
// 图片以 base64 字符串存 JSON 数组：不走对象存储，单条反馈最多 3 张小图，
// 对 SQLite 来说完全够用，也省掉了一套上传/签名/清理逻辑。
type Feedback struct {
	ID         int64    `json:"id"`
	UserID     int64    `json:"userId"`
	Username   string   `json:"username,omitempty"`
	Type       string   `json:"type"`
	Content    string   `json:"content"`
	Images     []string `json:"images"`
	WithLogs   bool     `json:"withLogs"`
	AppVersion string   `json:"appVersion"`
	Device     string   `json:"device"`
	Status     string   `json:"status"`
	Reply      string   `json:"reply"`
	CreatedAt  int64    `json:"createdAt"`
	UpdatedAt  int64    `json:"updatedAt"`
}

// CreateFeedback 落库一条反馈，返回自增 id。
func (s *Store) CreateFeedback(ctx context.Context, userID int64, f Feedback, nowMs int64) (int64, error) {
	images := f.Images
	if images == nil {
		images = []string{}
	}
	imagesJSON, err := json.Marshal(images)
	if err != nil {
		return 0, fmt.Errorf("encode images: %w", err)
	}
	res, err := s.db.ExecContext(ctx,
		`INSERT INTO feedback
           (user_id, type, content, images_json, with_logs, app_version, device, status, reply, created_at, updated_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, 'pending', '', ?, ?)`,
		userID, f.Type, f.Content, string(imagesJSON), boolInt(f.WithLogs),
		f.AppVersion, f.Device, nowMs, nowMs,
	)
	if err != nil {
		return 0, fmt.Errorf("insert feedback: %w", err)
	}
	return res.LastInsertId()
}

// ListFeedbackByUser 返回某个用户自己的反馈，最新在前。
func (s *Store) ListFeedbackByUser(ctx context.Context, userID int64, limit int) ([]Feedback, error) {
	if limit <= 0 || limit > 100 {
		limit = 50
	}
	rows, err := s.db.QueryContext(ctx,
		`SELECT id, user_id, type, content, images_json, with_logs, app_version, device,
                status, reply, created_at, updated_at
           FROM feedback WHERE user_id = ? ORDER BY id DESC LIMIT ?`, userID, limit)
	if err != nil {
		return nil, fmt.Errorf("query feedback: %w", err)
	}
	defer rows.Close()
	return scanFeedbackRows(rows)
}

// ListAllFeedback 供管理端查看，可按状态过滤。
func (s *Store) ListAllFeedback(ctx context.Context, status string, limit, offset int) ([]Feedback, int, error) {
	if limit <= 0 || limit > 200 {
		limit = 50
	}
	if offset < 0 {
		offset = 0
	}
	where := ""
	args := []any{}
	if status != "" && status != "all" {
		where = " WHERE f.status = ?"
		args = append(args, status)
	}

	var total int
	countSQL := "SELECT COUNT(*) FROM feedback f" + where
	if err := s.db.QueryRowContext(ctx, countSQL, args...).Scan(&total); err != nil {
		return nil, 0, fmt.Errorf("count feedback: %w", err)
	}

	query := `SELECT f.id, f.user_id, COALESCE(u.username,''), f.type, f.content, f.images_json,
                     f.with_logs, f.app_version, f.device, f.status, f.reply, f.created_at, f.updated_at
                FROM feedback f LEFT JOIN users u ON u.id = f.user_id` + where +
		` ORDER BY f.id DESC LIMIT ? OFFSET ?`
	rows, err := s.db.QueryContext(ctx, query, append(args, limit, offset)...)
	if err != nil {
		return nil, 0, fmt.Errorf("query feedback: %w", err)
	}
	defer rows.Close()
	list, err := scanAdminFeedbackRows(rows)
	return list, total, err
}

// ReplyFeedback 由管理端更新状态和回复。
func (s *Store) ReplyFeedback(ctx context.Context, id int64, status, reply string, nowMs int64) error {
	res, err := s.db.ExecContext(ctx,
		`UPDATE feedback SET status = ?, reply = ?, updated_at = ? WHERE id = ?`,
		status, reply, nowMs, id)
	if err != nil {
		return fmt.Errorf("update feedback: %w", err)
	}
	n, _ := res.RowsAffected()
	if n == 0 {
		return sql.ErrNoRows
	}
	return nil
}

func scanFeedbackRows(rows *sql.Rows) ([]Feedback, error) {
	out := make([]Feedback, 0, 16)
	for rows.Next() {
		var f Feedback
		var images string
		var withLogs int
		if err := rows.Scan(&f.ID, &f.UserID, &f.Type, &f.Content, &images, &withLogs,
			&f.AppVersion, &f.Device, &f.Status, &f.Reply, &f.CreatedAt, &f.UpdatedAt); err != nil {
			return nil, err
		}
		f.WithLogs = withLogs != 0
		f.Images = decodeFeedbackImages(images)
		out = append(out, f)
	}
	return out, rows.Err()
}

func scanAdminFeedbackRows(rows *sql.Rows) ([]Feedback, error) {
	out := make([]Feedback, 0, 16)
	for rows.Next() {
		var f Feedback
		var images string
		var withLogs int
		if err := rows.Scan(&f.ID, &f.UserID, &f.Username, &f.Type, &f.Content, &images,
			&withLogs, &f.AppVersion, &f.Device, &f.Status, &f.Reply, &f.CreatedAt, &f.UpdatedAt); err != nil {
			return nil, err
		}
		f.WithLogs = withLogs != 0
		f.Images = decodeFeedbackImages(images)
		out = append(out, f)
	}
	return out, rows.Err()
}

func decodeFeedbackImages(raw string) []string {
	if raw == "" {
		return []string{}
	}
	var images []string
	if err := json.Unmarshal([]byte(raw), &images); err != nil {
		return []string{}
	}
	return images
}

// NowMs 是 store 层统一的时间戳来源，便于测试注入。
func NowMs() int64 { return time.Now().UnixMilli() }

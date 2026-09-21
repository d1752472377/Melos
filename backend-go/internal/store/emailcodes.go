package store

import (
	"context"
	"database/sql"
	"errors"
)

// EmailCode is a row of the email_codes table.
type EmailCode struct {
	ID        int64
	Email     string
	Code      string
	Scene     string
	ExpiresAt int64
	Used      int
	CreatedAt int64
}

// SaveEmailCode stores a freshly generated verification code.
func (s *Store) SaveEmailCode(ctx context.Context, email, code, scene string, expiresAt, nowMs int64) (int64, error) {
	res, err := s.db.ExecContext(ctx,
		`INSERT INTO email_codes (email, code, scene, expires_at, used, created_at) VALUES (?, ?, ?, ?, 0, ?)`,
		email, code, scene, expiresAt, nowMs)
	if err != nil {
		return 0, err
	}
	return res.LastInsertId()
}

// FindLatestValidEmailCode returns the newest unused, unexpired code.
func (s *Store) FindLatestValidEmailCode(ctx context.Context, email, scene string, nowMs int64) (*EmailCode, error) {
	row := s.db.QueryRowContext(ctx,
		`SELECT id, email, code, scene, expires_at, used, created_at FROM email_codes
		 WHERE email = ? AND scene = ? AND used = 0 AND expires_at > ? ORDER BY id DESC LIMIT 1`,
		email, scene, nowMs)
	var ec EmailCode
	err := row.Scan(&ec.ID, &ec.Email, &ec.Code, &ec.Scene, &ec.ExpiresAt, &ec.Used, &ec.CreatedAt)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &ec, nil
}

// MarkEmailCodeUsed consumes a code (single use).
func (s *Store) MarkEmailCodeUsed(ctx context.Context, id int64) error {
	_, err := s.db.ExecContext(ctx, `UPDATE email_codes SET used = 1 WHERE id = ?`, id)
	return err
}

// GetLatestCodeSentTime returns created_at of the most recent code for the
// email+scene pair (0 when none exist), used for the 60s resend cooldown.
func (s *Store) GetLatestCodeSentTime(ctx context.Context, email, scene string) (int64, error) {
	var createdAt int64
	err := s.db.QueryRowContext(ctx,
		`SELECT created_at FROM email_codes WHERE email = ? AND scene = ? ORDER BY id DESC LIMIT 1`,
		email, scene).Scan(&createdAt)
	if errors.Is(err, sql.ErrNoRows) {
		return 0, nil
	}
	if err != nil {
		return 0, err
	}
	return createdAt, nil
}

package store

import (
	"context"
	"time"

	"melos-backend/internal/security"
)

// EnsureAdminUser makes sure the built-in administrator exists, creating it on
// first boot and repairing the role if needed (mirrors lib/seed.js).
func (s *Store) EnsureAdminUser(ctx context.Context, username, password string) (bool, error) {
	if username == "" {
		username = "admin"
	}
	existing, err := s.FindUserByUsername(ctx, username)
	if err != nil {
		return false, err
	}
	if existing == nil {
		salt := security.NewSalt()
		hash, err := security.HashPassword(password, salt)
		if err != nil {
			return false, err
		}
		nickname := "管理员"
		if _, err := s.CreateUser(ctx, username, hash, salt, &nickname, "admin", "", time.Now().UnixMilli()); err != nil {
			return false, err
		}
		return true, nil
	}
	if existing.Role != "admin" {
		if _, err := s.db.ExecContext(ctx, `UPDATE users SET role = ? WHERE id = ?`, "admin", existing.ID); err != nil {
			return false, err
		}
	}
	return false, nil
}

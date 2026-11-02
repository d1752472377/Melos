package store

import (
	"context"
	"crypto/rand"
	"database/sql"
	"encoding/hex"
	"errors"
	"fmt"
	"strings"
)

// User is a row of the users table.
type User struct {
	ID           int64
	Username     string
	PasswordHash string
	Salt         string
	Nickname     string
	Avatar       string
	Background   string
	Role         string
	Email        string
	CreatedAt    int64
	UpdatedAt    int64
	LastActiveAt int64
}

const userColumns = `id, username, password_hash, salt, nickname, avatar, background, role, email, created_at, updated_at, last_active_at`

type scanner interface{ Scan(dest ...any) error }

func scanUser(sc scanner) (*User, error) {
	var u User
	err := sc.Scan(&u.ID, &u.Username, &u.PasswordHash, &u.Salt, &u.Nickname, &u.Avatar,
		&u.Background, &u.Role, &u.Email, &u.CreatedAt, &u.UpdatedAt, &u.LastActiveAt)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &u, nil
}

// FindUserByUsername looks up a user by login name.
func (s *Store) FindUserByUsername(ctx context.Context, username string) (*User, error) {
	return scanUser(s.db.QueryRowContext(ctx, `SELECT `+userColumns+` FROM users WHERE username = ?`, username))
}

// FindUserByID looks up a user by primary key.
func (s *Store) FindUserByID(ctx context.Context, id int64) (*User, error) {
	return scanUser(s.db.QueryRowContext(ctx, `SELECT `+userColumns+` FROM users WHERE id = ?`, id))
}

// FindUserByEmail looks up a user by normalised (trimmed, lower-cased) email.
func (s *Store) FindUserByEmail(ctx context.Context, email string) (*User, error) {
	return scanUser(s.db.QueryRowContext(ctx, `SELECT `+userColumns+` FROM users WHERE email = ? AND email != ''`, email))
}

// CreateUser inserts a new user and returns the stored row.
func (s *Store) CreateUser(ctx context.Context, username, passwordHash, salt string, nickname *string, role, email string, nowMs int64) (*User, error) {
	nick := username
	if nickname != nil {
		nick = *nickname
	}
	if role == "" {
		role = "user"
	}
	res, err := s.db.ExecContext(ctx,
		`INSERT INTO users (username, password_hash, salt, nickname, role, email, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
		username, passwordHash, salt, nick, role, email, nowMs, nowMs)
	if err != nil {
		return nil, err
	}
	id, err := res.LastInsertId()
	if err != nil {
		return nil, err
	}
	return s.FindUserByID(ctx, id)
}

// UpdateUserProfile applies partial profile changes. nil leaves a field
// untouched; a pointer to "" clears it (mirrors the Node `?? ` semantics).
func (s *Store) UpdateUserProfile(ctx context.Context, id int64, nickname, avatar, background, email *string, nowMs int64) (*User, error) {
	cur, err := s.FindUserByID(ctx, id)
	if err != nil || cur == nil {
		return nil, err
	}
	apply := func(in *string, existing string) string {
		if in == nil {
			return existing
		}
		return *in
	}
	_, err = s.db.ExecContext(ctx,
		`UPDATE users SET nickname = ?, avatar = ?, background = ?, email = ?, updated_at = ? WHERE id = ?`,
		apply(nickname, cur.Nickname), apply(avatar, cur.Avatar), apply(background, cur.Background),
		apply(email, cur.Email), nowMs, id)
	if err != nil {
		return nil, err
	}
	return s.FindUserByID(ctx, id)
}

// UpdateUserPassword replaces the password hash + salt.
func (s *Store) UpdateUserPassword(ctx context.Context, id int64, passwordHash, salt string, nowMs int64) error {
	_, err := s.db.ExecContext(ctx, `UPDATE users SET password_hash = ?, salt = ?, updated_at = ? WHERE id = ?`,
		passwordHash, salt, nowMs, id)
	return err
}

// ListUsers returns a page of users ordered newest-first.
func (s *Store) ListUsers(ctx context.Context, offset, limit int, search, role string) ([]*User, error) {
	sqlStr := `SELECT ` + userColumns + ` FROM users WHERE 1=1`
	args := []any{}
	if search != "" {
		sqlStr += ` AND (username LIKE ? OR nickname LIKE ? OR email LIKE ?)`
		like := "%" + search + "%"
		args = append(args, like, like, like)
	}
	if role != "" {
		sqlStr += ` AND role = ?`
		args = append(args, role)
	}
	sqlStr += ` ORDER BY id DESC LIMIT ? OFFSET ?`
	args = append(args, limit, offset)

	rows, err := s.db.QueryContext(ctx, sqlStr, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var out []*User
	for rows.Next() {
		u, err := scanUser(rows)
		if err != nil {
			return nil, err
		}
		out = append(out, u)
	}
	return out, rows.Err()
}

// CountUsers counts users matching the optional filters.
func (s *Store) CountUsers(ctx context.Context, search, role string) (int64, error) {
	sqlStr := `SELECT COUNT(*) AS total FROM users WHERE 1=1`
	args := []any{}
	if search != "" {
		sqlStr += ` AND (username LIKE ? OR nickname LIKE ? OR email LIKE ?)`
		like := "%" + search + "%"
		args = append(args, like, like, like)
	}
	if role != "" {
		sqlStr += ` AND role = ?`
		args = append(args, role)
	}
	var total int64
	err := s.db.QueryRowContext(ctx, sqlStr, args...).Scan(&total)
	return total, err
}

// AdminUpdateParams carries the optional fields of an admin user update.
type AdminUpdateParams struct {
	Nickname     *string
	Avatar       *string
	Role         *string
	PasswordHash *string
	Salt         *string
}

// AdminUpdateUser applies a partial update and returns the refreshed row.
func (s *Store) AdminUpdateUser(ctx context.Context, id int64, p AdminUpdateParams, nowMs int64) (*User, error) {
	if _, err := s.FindUserByID(ctx, id); err != nil {
		return nil, err
	}
	sets := []string{"updated_at = ?"}
	args := []any{nowMs}
	add := func(col string, val any) {
		sets = append(sets, col+" = ?")
		args = append(args, val)
	}
	if p.Nickname != nil {
		add("nickname", *p.Nickname)
	}
	if p.Avatar != nil {
		add("avatar", *p.Avatar)
	}
	if p.Role != nil {
		add("role", *p.Role)
	}
	if p.PasswordHash != nil && p.Salt != nil {
		add("password_hash", *p.PasswordHash)
		add("salt", *p.Salt)
	}
	args = append(args, id)
	if _, err := s.db.ExecContext(ctx, `UPDATE users SET `+strings.Join(sets, ", ")+` WHERE id = ?`, args...); err != nil {
		return nil, err
	}
	return s.FindUserByID(ctx, id)
}

// AdminDeleteUser cascade-deletes every piece of data owned by the user.
func (s *Store) AdminDeleteUser(ctx context.Context, id int64) error {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer func() { _ = tx.Rollback() }()

	tables := []string{"play_events", "playlist_songs", "playlists", "history", "favorites",
		"downloads", "user_scripts", "user_state", "sessions"}
	for _, t := range tables {
		if _, err := tx.ExecContext(ctx, `DELETE FROM `+t+` WHERE user_id = ?`, id); err != nil {
			return err
		}
	}
	if _, err := tx.ExecContext(ctx, `DELETE FROM users WHERE id = ?`, id); err != nil {
		return err
	}
	return tx.Commit()
}

// PublicUser renders a user with the exact key set the clients expect:
// snake_case (music-admin) and the createdAt camelCase alias (Android).
func PublicUser(u *User) map[string]any {
	if u == nil {
		return nil
	}
	nickname := u.Nickname
	if nickname == "" {
		nickname = u.Username
	}
	role := u.Role
	if role == "" {
		role = "user"
	}
	return map[string]any{
		"id":         u.ID,
		"username":   u.Username,
		"nickname":   nickname,
		"avatar":     u.Avatar,
		"background": u.Background,
		"role":       role,
		"email":      u.Email,
		"created_at":    u.CreatedAt,
		"updated_at":    u.UpdatedAt,
		"createdAt":     u.CreatedAt,
		"last_active_at": u.LastActiveAt,
		"lastActiveAt":  u.LastActiveAt,
	}
}

// TouchLastActive records the user's most recent activity time. Cheap enough
// to call from the auth middleware; callers should throttle (see server.go).
func (s *Store) TouchLastActive(ctx context.Context, id int64, nowMs int64) error {
	_, err := s.db.ExecContext(ctx, `UPDATE users SET last_active_at = ? WHERE id = ?`, nowMs, id)
	return err
}

// ==================== sessions ====================

// SessionUser is the join of a session with its owning user.
type SessionUser struct {
	SessionID        string
	UserID           int64
	TokenHash        string
	DeviceName       string
	SessionCreatedAt int64
	LastSeenAt       int64
	ExpiresAt        int64
	RevokedAt        sql.NullInt64
	User             User
}

// CreateSession issues a new device session, pruning expired rows and evicting
// the least-recently-used session when the per-user cap is exceeded.
func (s *Store) CreateSession(ctx context.Context, userID int64, tokenHash, deviceName string, ttlMs int64, maxSessions int, nowMs int64) (string, int64, error) {
	if _, err := s.db.ExecContext(ctx, `DELETE FROM sessions WHERE expires_at < ?`, nowMs); err != nil {
		return "", 0, err
	}
	var count int
	if err := s.db.QueryRowContext(ctx,
		`SELECT COUNT(*) FROM sessions WHERE user_id = ? AND revoked_at IS NULL`, userID).Scan(&count); err != nil {
		return "", 0, err
	}
	if count >= maxSessions {
		if _, err := s.db.ExecContext(ctx,
			`DELETE FROM sessions WHERE id IN (SELECT id FROM sessions WHERE user_id = ? AND revoked_at IS NULL ORDER BY last_seen_at ASC LIMIT ?)`,
			userID, count-maxSessions+1); err != nil {
			return "", 0, err
		}
	}
	id := newUUID()
	expiresAt := nowMs + ttlMs
	_, err := s.db.ExecContext(ctx,
		`INSERT INTO sessions (id, user_id, token_hash, device_name, created_at, last_seen_at, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?)`,
		id, userID, tokenHash, deviceName, nowMs, nowMs, expiresAt)
	if err != nil {
		return "", 0, err
	}
	return id, expiresAt, nil
}

// FindSessionByTokenHash resolves a session (with user) by token hash.
func (s *Store) FindSessionByTokenHash(ctx context.Context, tokenHash string) (*SessionUser, error) {
	row := s.db.QueryRowContext(ctx,
		`SELECT s.id, s.user_id, s.token_hash, s.device_name, s.created_at, s.last_seen_at, s.expires_at, s.revoked_at,
		        u.id, u.username, u.password_hash, u.salt, u.nickname, u.avatar, u.background, u.role, u.email, u.created_at, u.updated_at
		 FROM sessions s JOIN users u ON u.id = s.user_id
		 WHERE s.token_hash = ?`, tokenHash)

	var su SessionUser
	err := row.Scan(&su.SessionID, &su.UserID, &su.TokenHash, &su.DeviceName, &su.SessionCreatedAt,
		&su.LastSeenAt, &su.ExpiresAt, &su.RevokedAt,
		&su.User.ID, &su.User.Username, &su.User.PasswordHash, &su.User.Salt, &su.User.Nickname,
		&su.User.Avatar, &su.User.Background, &su.User.Role, &su.User.Email, &su.User.CreatedAt, &su.User.UpdatedAt)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &su, nil
}

// TouchSession refreshes last_seen_at.
func (s *Store) TouchSession(ctx context.Context, id string, nowMs int64) error {
	_, err := s.db.ExecContext(ctx, `UPDATE sessions SET last_seen_at = ? WHERE id = ?`, nowMs, id)
	return err
}

// RevokeSessionByTokenHash deletes the session (logout is immediate).
func (s *Store) RevokeSessionByTokenHash(ctx context.Context, tokenHash string) error {
	_, err := s.db.ExecContext(ctx, `DELETE FROM sessions WHERE token_hash = ?`, tokenHash)
	return err
}

// RevokeSessionByID deletes a single owned session.
func (s *Store) RevokeSessionByID(ctx context.Context, userID int64, id string) error {
	_, err := s.db.ExecContext(ctx, `DELETE FROM sessions WHERE id = ? AND user_id = ?`, id, userID)
	return err
}

// RevokeAllSessions deletes every session of the user, optionally sparing one.
func (s *Store) RevokeAllSessions(ctx context.Context, userID int64, exceptID string) error {
	if exceptID != "" {
		_, err := s.db.ExecContext(ctx, `DELETE FROM sessions WHERE user_id = ? AND id != ?`, userID, exceptID)
		return err
	}
	_, err := s.db.ExecContext(ctx, `DELETE FROM sessions WHERE user_id = ?`, userID)
	return err
}

// SessionInfo is the shape returned by GET /api/me/sessions.
type SessionInfo struct {
	ID         string
	DeviceName string
	CreatedAt  int64
	LastSeenAt int64
	ExpiresAt  int64
}

// ListSessions returns active sessions ordered by most recently seen.
func (s *Store) ListSessions(ctx context.Context, userID int64) ([]SessionInfo, error) {
	rows, err := s.db.QueryContext(ctx,
		`SELECT id, device_name, created_at, last_seen_at, expires_at FROM sessions WHERE user_id = ? AND revoked_at IS NULL ORDER BY last_seen_at DESC`,
		userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var out []SessionInfo
	for rows.Next() {
		var si SessionInfo
		if err := rows.Scan(&si.ID, &si.DeviceName, &si.CreatedAt, &si.LastSeenAt, &si.ExpiresAt); err != nil {
			return nil, err
		}
		out = append(out, si)
	}
	return out, rows.Err()
}

// PruneExpiredSessions removes expired sessions.
func (s *Store) PruneExpiredSessions(ctx context.Context, nowMs int64) (int64, error) {
	res, err := s.db.ExecContext(ctx, `DELETE FROM sessions WHERE expires_at < ?`, nowMs)
	if err != nil {
		return 0, err
	}
	n, _ := res.RowsAffected()
	return n, nil
}

// PruneOldPlayEvents drops play events older than the given number of days.
func (s *Store) PruneOldPlayEvents(ctx context.Context, days int, nowMs int64) (int64, error) {
	cutoff := nowMs - int64(days)*24*60*60*1000
	res, err := s.db.ExecContext(ctx, `DELETE FROM play_events WHERE listened_at_ms < ? AND listened_at_ms > 0`, cutoff)
	if err != nil {
		return 0, err
	}
	n, _ := res.RowsAffected()
	return n, nil
}

// newUUID returns a random RFC-4122 v4 UUID string (matches crypto.randomUUID).
func newUUID() string {
	var b [16]byte
	if _, err := rand.Read(b[:]); err != nil {
		panic(fmt.Sprintf("crypto/rand failed: %v", err))
	}
	b[6] = (b[6] & 0x0f) | 0x40
	b[8] = (b[8] & 0x3f) | 0x80
	dst := make([]byte, 36)
	hex.Encode(dst[0:8], b[0:4])
	dst[8] = '-'
	hex.Encode(dst[9:13], b[4:6])
	dst[13] = '-'
	hex.Encode(dst[14:18], b[6:8])
	dst[18] = '-'
	hex.Encode(dst[19:23], b[8:10])
	dst[23] = '-'
	hex.Encode(dst[24:36], b[10:16])
	return string(dst)
}

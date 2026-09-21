package store

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
	"strings"
)

// AppVersion is one published APK release (table app_versions).
type AppVersion struct {
	ID          int64
	VersionCode int64
	VersionName string
	ApkURL      string
	FileSize    int64
	HashSHA256  string
	Changelog   string
	ForceUpdate bool
	Enabled     bool
	CreatedAt   string
}

const versionColumns = `id, version_code, version_name, apk_url, file_size, hash_sha256, changelog, force_update, enabled, created_at`

func scanVersion(sc scanner) (*AppVersion, error) {
	var v AppVersion
	var force, enabled sql.NullInt64
	var createdAt any
	err := sc.Scan(&v.ID, &v.VersionCode, &v.VersionName, &v.ApkURL, &v.FileSize, &v.HashSHA256,
		&v.Changelog, &force, &enabled, &createdAt)
	if errors.Is(err, sql.ErrNoRows) {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	v.ForceUpdate = force.Valid && force.Int64 != 0
	v.Enabled = enabled.Valid && enabled.Int64 != 0
	// created_at uses the DDL's DATETIME DEFAULT CURRENT_TIMESTAMP: render
	// whatever the driver returns (text or time) as a string.
	v.CreatedAt = fmt.Sprint(createdAt)
	return &v, nil
}

// ListVersions returns one page of versions, newest version_code first.
func (s *Store) ListVersions(ctx context.Context, offset, limit int) ([]*AppVersion, error) {
	rows, err := s.db.QueryContext(ctx,
		`SELECT `+versionColumns+` FROM app_versions ORDER BY version_code DESC LIMIT ? OFFSET ?`, limit, offset)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	out := []*AppVersion{}
	for rows.Next() {
		v, err := scanVersion(rows)
		if err != nil {
			return nil, err
		}
		out = append(out, v)
	}
	return out, rows.Err()
}

// CountVersions returns the total number of published versions.
func (s *Store) CountVersions(ctx context.Context) (int64, error) {
	var total int64
	err := s.db.QueryRowContext(ctx, `SELECT COUNT(*) FROM app_versions`).Scan(&total)
	return total, err
}

// FindVersionByID looks up a version by primary key.
func (s *Store) FindVersionByID(ctx context.Context, id int64) (*AppVersion, error) {
	return scanVersion(s.db.QueryRowContext(ctx, `SELECT `+versionColumns+` FROM app_versions WHERE id = ?`, id))
}

// FindVersionByCode looks up a version by version_code (unique).
func (s *Store) FindVersionByCode(ctx context.Context, code int64) (*AppVersion, error) {
	return scanVersion(s.db.QueryRowContext(ctx, `SELECT `+versionColumns+` FROM app_versions WHERE version_code = ?`, code))
}

// LatestEnabledVersion returns the highest enabled version, or nil when none.
func (s *Store) LatestEnabledVersion(ctx context.Context) (*AppVersion, error) {
	return scanVersion(s.db.QueryRowContext(ctx,
		`SELECT `+versionColumns+` FROM app_versions WHERE enabled = 1 ORDER BY version_code DESC LIMIT 1`))
}

// CreateVersionParams carries the fields of a new release.
type CreateVersionParams struct {
	VersionCode int64
	VersionName string
	ApkURL      string
	FileSize    int64
	HashSHA256  string
	Changelog   string
	ForceUpdate bool
	Enabled     bool
}

// CreateVersion inserts a release row (created_at is filled by the DB default).
func (s *Store) CreateVersion(ctx context.Context, p CreateVersionParams) (*AppVersion, error) {
	res, err := s.db.ExecContext(ctx,
		`INSERT INTO app_versions (version_code, version_name, apk_url, file_size, hash_sha256, changelog, force_update, enabled)
		 VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
		p.VersionCode, p.VersionName, p.ApkURL, p.FileSize, p.HashSHA256, p.Changelog, boolInt(p.ForceUpdate), boolInt(p.Enabled))
	if err != nil {
		return nil, err
	}
	id, err := res.LastInsertId()
	if err != nil {
		return nil, err
	}
	return s.FindVersionByID(ctx, id)
}

// UpdateVersionParams carries the mutable fields of a release. nil = unchanged.
type UpdateVersionParams struct {
	Enabled     *bool
	Changelog   *string
	ForceUpdate *bool
}

// UpdateVersion applies a partial update and returns the refreshed row.
func (s *Store) UpdateVersion(ctx context.Context, id int64, p UpdateVersionParams) (*AppVersion, error) {
	if _, err := s.FindVersionByID(ctx, id); err != nil {
		return nil, err
	}
	sets := []string{}
	args := []any{}
	if p.Enabled != nil {
		sets = append(sets, "enabled = ?")
		args = append(args, boolInt(*p.Enabled))
	}
	if p.Changelog != nil {
		sets = append(sets, "changelog = ?")
		args = append(args, *p.Changelog)
	}
	if p.ForceUpdate != nil {
		sets = append(sets, "force_update = ?")
		args = append(args, boolInt(*p.ForceUpdate))
	}
	if len(sets) > 0 {
		args = append(args, id)
		if _, err := s.db.ExecContext(ctx, `UPDATE app_versions SET `+strings.Join(sets, ", ")+` WHERE id = ?`, args...); err != nil {
			return nil, err
		}
	}
	return s.FindVersionByID(ctx, id)
}

// DeleteVersion removes a release row and returns the deleted record so the
// caller can also drop the local APK file.
func (s *Store) DeleteVersion(ctx context.Context, id int64) (*AppVersion, error) {
	row, err := s.FindVersionByID(ctx, id)
	if err != nil || row == nil {
		return nil, err
	}
	if _, err := s.db.ExecContext(ctx, `DELETE FROM app_versions WHERE id = ?`, id); err != nil {
		return nil, err
	}
	return row, nil
}

// Public renders a release for API responses. Both snake_case (DDL/admin) and
// camelCase aliases are emitted, mirroring the user object convention.
func (v *AppVersion) Public() map[string]any {
	if v == nil {
		return nil
	}
	return map[string]any{
		"id":           v.ID,
		"version_code": v.VersionCode,
		"version_name": v.VersionName,
		"apk_url":      v.ApkURL,
		"file_size":    v.FileSize,
		"hash_sha256":  v.HashSHA256,
		"changelog":    v.Changelog,
		"force_update": v.ForceUpdate,
		"enabled":      v.Enabled,
		"created_at":   v.CreatedAt,
		"versionCode":  v.VersionCode,
		"versionName":  v.VersionName,
		"apkUrl":       v.ApkURL,
		"fileSize":     v.FileSize,
		"hashSha256":   v.HashSHA256,
		"forceUpdate":  v.ForceUpdate,
		"createdAt":    v.CreatedAt,
	}
}

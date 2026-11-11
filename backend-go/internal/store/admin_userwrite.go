package store

import (
	"context"
	"database/sql"
	"errors"
	"fmt"
)

// ErrNoRows 表示"这一行本来就不存在"。
//
// 管理端的删除操作以前无论有没有命中都回 200，管理端会以为数据已被清掉；
// 现在删除类函数在零命中时返回它，接口据此回 404，同时整个事务回滚，
// 不会白白推进 sync_seq（否则用户设备会拉到一个空增量）。
var ErrNoRows = errors.New("no rows affected")

// 管理员对用户数据的写操作。
//
// ## 为什么不能直接 UPDATE 表了事
// 客户端同步是 LWW + 墓碑模型：设备拉取时按 `update_seq > 游标` 取增量，
// 游标来自 `user_state.sync_seq`。管理员直接改表而不推进 `sync_seq`，
// 用户的所有设备**永远看不到这次修改**（下次推送还会把它覆盖回去）。
//
// 所以这里每个写操作都走同一个套路：
//   1. `user_state.sync_seq + 1`；
//   2. 用这个新序号写行（`update_seq = seq`、`updated_at_ms = now`）；
//   3. 删除一律写墓碑（`deleted = 1`），而不是物理删除——否则另一台设备
//      的旧快照会在下次推送时把这条记录"写活"。
//
// 语义上等同于"管理员这台设备刚做了一次同步"，因此完全复用客户端那条链路。

// adminWriteTx runs fn inside a transaction with a freshly bumped sync_seq.
func (s *Store) adminWriteTx(ctx context.Context, userID, nowMs int64, fn func(tx *sql.Tx, seq int64) error) error {
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer func() { _ = tx.Rollback() }()

	if _, err := tx.ExecContext(ctx,
		`INSERT INTO user_state (user_id, sync_seq) VALUES (?, 0) ON CONFLICT(user_id) DO NOTHING`, userID); err != nil {
		return err
	}
	if _, err := tx.ExecContext(ctx,
		`UPDATE user_state SET sync_seq = sync_seq + 1 WHERE user_id = ?`, userID); err != nil {
		return err
	}
	var seq int64
	if err := tx.QueryRowContext(ctx,
		`SELECT sync_seq FROM user_state WHERE user_id = ?`, userID).Scan(&seq); err != nil {
		return err
	}
	if err := fn(tx, seq); err != nil {
		return err
	}
	return tx.Commit()
}

// ==================== 音源脚本 ====================

// AdminSaveScript creates or updates a user script.
func (s *Store) AdminSaveScript(ctx context.Context, userID int64, scriptID, name, description, code string, enabled bool, nowMs int64) error {
	if scriptID == "" {
		return fmt.Errorf("脚本 id 不能为空")
	}
	return s.adminWriteTx(ctx, userID, nowMs, func(tx *sql.Tx, seq int64) error {
		_, err := tx.ExecContext(ctx, `
			INSERT INTO user_scripts (user_id, script_id, name, description, code, enabled, update_seq, updated_at_ms, deleted)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
			ON CONFLICT (user_id, script_id) DO UPDATE SET
				name = excluded.name, description = excluded.description, code = excluded.code,
				enabled = excluded.enabled, update_seq = excluded.update_seq,
				updated_at_ms = excluded.updated_at_ms, deleted = 0`,
			userID, scriptID, name, description, code, boolInt(enabled), seq, nowMs)
		return err
	})
}

// AdminDeleteScript tombstones a user script and reports how many rows matched.
//
// 返回受影响行数是为了让接口能区分"删掉了"和"本来就不存在"：
// 之前无论有没有命中都回 200「脚本已删除」，管理端会以为用户设备上的脚本被清掉了。
// 注意命中的可能是已有墓碑行（deleted=1），SQLite 的 RowsAffected 统计的是
// WHERE 匹配的行数，所以重复删除依然返回 1，不会误报 404。
func (s *Store) AdminDeleteScript(ctx context.Context, userID int64, scriptID string, nowMs int64) (int64, error) {
	var n int64
	err := s.adminWriteTx(ctx, userID, nowMs, func(tx *sql.Tx, seq int64) error {
		res, err := tx.ExecContext(ctx, `
			UPDATE user_scripts SET deleted = 1, update_seq = ?, updated_at_ms = ?
			WHERE user_id = ? AND script_id = ?`, seq, nowMs, userID, scriptID)
		if err != nil {
			return err
		}
		if n, _ = res.RowsAffected(); n == 0 {
			return ErrNoRows
		}
		return nil
	})
	return n, err
}

// ==================== 收藏 ====================

// AdminAddFavorite upserts one favourite (songJSON is the full song payload).
func (s *Store) AdminAddFavorite(ctx context.Context, userID int64, platform, songID, songJSON string, nowMs int64) error {
	if platform == "" || songID == "" {
		return fmt.Errorf("platform 与歌曲 id 不能为空")
	}
	return s.adminWriteTx(ctx, userID, nowMs, func(tx *sql.Tx, seq int64) error {
		_, err := tx.ExecContext(ctx, `
			INSERT INTO favorites (user_id, platform, song_id, song_json, update_seq, updated_at_ms, deleted)
			VALUES (?, ?, ?, ?, ?, ?, 0)
			ON CONFLICT (user_id, platform, song_id) DO UPDATE SET
				song_json = excluded.song_json, update_seq = excluded.update_seq,
				updated_at_ms = excluded.updated_at_ms, deleted = 0`,
			userID, platform, songID, songJSON, seq, nowMs)
		return err
	})
}

// AdminDeleteFavorite tombstones one favourite.
func (s *Store) AdminDeleteFavorite(ctx context.Context, userID int64, platform, songID string, nowMs int64) (int64, error) {
	var n int64
	err := s.adminWriteTx(ctx, userID, nowMs, func(tx *sql.Tx, seq int64) error {
		res, err := tx.ExecContext(ctx, `
			UPDATE favorites SET deleted = 1, update_seq = ?, updated_at_ms = ?
			WHERE user_id = ? AND platform = ? AND song_id = ?`, seq, nowMs, userID, platform, songID)
		if err != nil {
			return err
		}
		if n, _ = res.RowsAffected(); n == 0 {
			return ErrNoRows
		}
		return nil
	})
	return n, err
}

// ==================== 歌单 ====================

// AdminUpsertPlaylist creates or renames a playlist.
func (s *Store) AdminUpsertPlaylist(ctx context.Context, userID int64, playlistID, kind, name, coverURL string, nowMs int64) error {
	if playlistID == "" {
		return fmt.Errorf("歌单 id 不能为空")
	}
	if kind != "fav" {
		kind = "mine"
	}
	return s.adminWriteTx(ctx, userID, nowMs, func(tx *sql.Tx, seq int64) error {
		_, err := tx.ExecContext(ctx, `
			INSERT INTO playlists (user_id, playlist_id, kind, name, cover_url, update_seq, updated_at_ms, deleted)
			VALUES (?, ?, ?, ?, ?, ?, ?, 0)
			ON CONFLICT (user_id, playlist_id) DO UPDATE SET
				kind = excluded.kind, name = excluded.name, cover_url = excluded.cover_url,
				update_seq = excluded.update_seq, updated_at_ms = excluded.updated_at_ms, deleted = 0`,
			userID, playlistID, kind, name, coverURL, seq, nowMs)
		return err
	})
}

// AdminDeletePlaylist tombstones a playlist and all of its songs.
//
// 返回歌单行是否命中：歌单本来就不存在时不再连带更新歌曲表，
// 也让接口能回 404 而不是假成功。
func (s *Store) AdminDeletePlaylist(ctx context.Context, userID int64, playlistID string, nowMs int64) (int64, error) {
	var n int64
	err := s.adminWriteTx(ctx, userID, nowMs, func(tx *sql.Tx, seq int64) error {
		res, err := tx.ExecContext(ctx, `
			UPDATE playlists SET deleted = 1, update_seq = ?, updated_at_ms = ?
			WHERE user_id = ? AND playlist_id = ?`, seq, nowMs, userID, playlistID)
		if err != nil {
			return err
		}
		if n, _ = res.RowsAffected(); n == 0 {
			return ErrNoRows
		}
		_, err = tx.ExecContext(ctx, `
			UPDATE playlist_songs SET deleted = 1, update_seq = ?, updated_at_ms = ?
			WHERE user_id = ? AND playlist_id = ?`, seq, nowMs, userID, playlistID)
		return err
	})
	return n, err
}

// AdminUpsertPlaylistSong adds or repositions one track inside a playlist.
func (s *Store) AdminUpsertPlaylistSong(ctx context.Context, userID int64, playlistID, platform, songID, songJSON string, position int64, nowMs int64) error {
	if playlistID == "" || platform == "" || songID == "" {
		return fmt.Errorf("歌单 id / platform / 歌曲 id 都不能为空")
	}
	if position < 0 {
		position = 0
	}
	return s.adminWriteTx(ctx, userID, nowMs, func(tx *sql.Tx, seq int64) error {
		_, err := tx.ExecContext(ctx, `
			INSERT INTO playlist_songs (user_id, playlist_id, platform, song_id, position, song_json, update_seq, updated_at_ms, deleted)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
			ON CONFLICT (user_id, playlist_id, platform, song_id) DO UPDATE SET
				position = excluded.position, song_json = excluded.song_json,
				update_seq = excluded.update_seq, updated_at_ms = excluded.updated_at_ms, deleted = 0`,
			userID, playlistID, platform, songID, position, songJSON, seq, nowMs)
		return err
	})
}

// AdminDeletePlaylistSong tombstones one track of a playlist.
func (s *Store) AdminDeletePlaylistSong(ctx context.Context, userID int64, playlistID, platform, songID string, nowMs int64) (int64, error) {
	var n int64
	err := s.adminWriteTx(ctx, userID, nowMs, func(tx *sql.Tx, seq int64) error {
		res, err := tx.ExecContext(ctx, `
			UPDATE playlist_songs SET deleted = 1, update_seq = ?, updated_at_ms = ?
			WHERE user_id = ? AND playlist_id = ? AND platform = ? AND song_id = ?`,
			seq, nowMs, userID, playlistID, platform, songID)
		if err != nil {
			return err
		}
		if n, _ = res.RowsAffected(); n == 0 {
			return ErrNoRows
		}
		return nil
	})
	return n, err
}

// ==================== 听歌历史 / 下载记录 ====================

// AdminDeleteHistory tombstones one history entry.
func (s *Store) AdminDeleteHistory(ctx context.Context, userID int64, platform, songID string, nowMs int64) (int64, error) {
	var n int64
	err := s.adminWriteTx(ctx, userID, nowMs, func(tx *sql.Tx, seq int64) error {
		res, err := tx.ExecContext(ctx, `
			UPDATE history SET deleted = 1, update_seq = ?, updated_at_ms = ?
			WHERE user_id = ? AND platform = ? AND song_id = ?`, seq, nowMs, userID, platform, songID)
		if err != nil {
			return err
		}
		if n, _ = res.RowsAffected(); n == 0 {
			return ErrNoRows
		}
		return nil
	})
	return n, err
}

// AdminClearHistory tombstones every history entry of the user.
func (s *Store) AdminClearHistory(ctx context.Context, userID int64, nowMs int64) (int64, error) {
	var n int64
	err := s.adminWriteTx(ctx, userID, nowMs, func(tx *sql.Tx, seq int64) error {
		res, err := tx.ExecContext(ctx, `
			UPDATE history SET deleted = 1, update_seq = ?, updated_at_ms = ?
			WHERE user_id = ? AND deleted = 0`, seq, nowMs, userID)
		if err != nil {
			return err
		}
		n, _ = res.RowsAffected()
		return nil
	})
	return n, err
}

// AdminDeleteDownload tombstones one per-device download record.
func (s *Store) AdminDeleteDownload(ctx context.Context, userID int64, platform, songID, deviceID string, nowMs int64) (int64, error) {
	var n int64
	err := s.adminWriteTx(ctx, userID, nowMs, func(tx *sql.Tx, seq int64) error {
		res, err := tx.ExecContext(ctx, `
			UPDATE downloads SET deleted = 1, update_seq = ?, updated_at_ms = ?
			WHERE user_id = ? AND platform = ? AND song_id = ? AND device_id = ?`,
			seq, nowMs, userID, platform, songID, deviceID)
		if err != nil {
			return err
		}
		if n, _ = res.RowsAffected(); n == 0 {
			return ErrNoRows
		}
		return nil
	})
	return n, err
}

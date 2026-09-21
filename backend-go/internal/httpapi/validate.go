package httpapi

import (
	"crypto/rand"
	"fmt"
	"math/big"
	"regexp"
	"strconv"
	"strings"
	"unicode/utf8"
)

// usernameRe mirrors the Node regex: 2-20 chars of letters, digits, underscore
// or CJK Unified Ideographs (U+4E00-U+9FA5).
var usernameRe = regexp.MustCompile(`^[a-zA-Z0-9_\x{4e00}-\x{9fa5}]{2,20}$`)

// emailRe is the pragmatic x@y.zz check used by the Node backend.
var emailRe = regexp.MustCompile(`^[^\s@]+@[^\s@]+\.[^\s@]{2,}$`)

const (
	emailMaxLen    = 120
	nicknameMaxLen = 24
)

// asString coerces a decoded JSON value to a string, mirroring JS String(v).
func asString(v any) string {
	switch t := v.(type) {
	case nil:
		return ""
	case string:
		return t
	case bool:
		if t {
			return "true"
		}
		return "false"
	case float64:
		return strconv.FormatFloat(t, 'f', -1, 64)
	default:
		return fmt.Sprintf("%v", t)
	}
}

// isTruthy mirrors JS truthiness for the values we receive from JSON.
func isTruthy(v any) bool {
	switch t := v.(type) {
	case nil:
		return false
	case bool:
		return t
	case float64:
		return t != 0
	case string:
		return t != ""
	default:
		return true
	}
}

// normalizeEmail trims and lower-cases the address and validates it.
// A non-empty result with ok=true is a bindable email; "" means unbind.
func normalizeEmail(raw any) (email string, ok bool, errMsg string) {
	v := strings.ToLower(strings.TrimSpace(asString(raw)))
	if v == "" {
		return "", true, ""
	}
	if utf8.RuneCountInString(v) > emailMaxLen || !emailRe.MatchString(v) {
		return "", false, "邮箱格式不正确（示例：name@example.com）"
	}
	return v, true, ""
}

// validateRegister mirrors the Node register validation.
func validateRegister(username string, password, nickname any) string {
	if !usernameRe.MatchString(username) {
		return "用户名需 2-20 位（字母/数字/下划线/中文）"
	}
	pw, isStr := password.(string)
	if !isStr || utf8.RuneCountInString(pw) < 5 || utf8.RuneCountInString(pw) > 64 {
		return "密码需 5-64 位"
	}
	if nickname != nil && utf8.RuneCountInString(asString(nickname)) > nicknameMaxLen {
		return "昵称最长 24 字"
	}
	return ""
}

// isEmailConflict detects a UNIQUE violation on users.email.
func isEmailConflict(err error) bool {
	if err == nil {
		return false
	}
	return strings.Contains(strings.ToLower(err.Error()), "unique constraint failed: users.email")
}

// randomInt returns a uniform random int in [min, max).
func randomInt(min, max int) int {
	n := max - min
	if n <= 0 {
		return min
	}
	v, err := rand.Int(rand.Reader, big.NewInt(int64(n)))
	if err != nil {
		panic(fmt.Sprintf("crypto/rand failed: %v", err))
	}
	return min + int(v.Int64())
}

// randomBase36 returns n random base36 characters.
func randomBase36(n int) string {
	const alphabet = "0123456789abcdefghijklmnopqrstuvwxyz"
	b := make([]byte, n)
	for i := range b {
		b[i] = alphabet[randomInt(0, len(alphabet))]
	}
	return string(b)
}

// sanitizeUsernameSegment mirrors `replace(/[^a-zA-Z0-9_\u4e00-\u9fa5]/g, '_')`.
var invalidUsernameChar = regexp.MustCompile(`[^a-zA-Z0-9_\x{4e00}-\x{9fa5}]`)

func sanitizeUsernameSegment(s string) string {
	return invalidUsernameChar.ReplaceAllString(s, "_")
}

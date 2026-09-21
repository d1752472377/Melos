package httpapi

import (
	"strings"
	"unicode/utf8"
)

func runeLen(s string) int { return utf8.RuneCountInString(s) }

func trimSpace(s string) string { return strings.TrimSpace(s) }

// optionalString returns a pointer to the coerced value when the key is present
// and non-null (mirrors the Node `value != null ? String(value) : undefined`).
func optionalString(body map[string]any, key string) *string {
	v, ok := body[key]
	if !ok || v == nil {
		return nil
	}
	s := asString(v)
	return &s
}

// Package security holds the auth primitives: password hashing, opaque tokens
// and the in-memory brute-force lockout.
package security

import (
	"crypto/rand"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/base64"
	"encoding/hex"
	"fmt"

	"golang.org/x/crypto/scrypt"
)

// scrypt parameters must match Node's crypto.scrypt defaults (N=16384, r=8,
// p=1) so that existing password hashes in data/music.db remain valid.
const (
	scryptN     = 16384
	scryptR     = 8
	scryptP     = 1
	scryptKeyLn = 64
)

// NewSalt returns a 16-byte random hex salt (matches crypto.randomBytes(16)).
func NewSalt() string {
	b := make([]byte, 16)
	if _, err := rand.Read(b); err != nil {
		panic(fmt.Sprintf("crypto/rand failed: %v", err))
	}
	return hex.EncodeToString(b)
}

// HashPassword derives a scrypt hash and returns it hex-encoded.
func HashPassword(password, salt string) (string, error) {
	key, err := scrypt.Key([]byte(password), []byte(salt), scryptN, scryptR, scryptP, scryptKeyLn)
	if err != nil {
		return "", err
	}
	return hex.EncodeToString(key), nil
}

// VerifyPassword re-derives the hash and compares in constant time.
func VerifyPassword(password, salt, expectedHex string) bool {
	derived, err := HashPassword(password, salt)
	if err != nil {
		return false
	}
	a, err := hex.DecodeString(derived)
	if err != nil {
		return false
	}
	b, err := hex.DecodeString(expectedHex)
	if err != nil || len(a) != len(b) {
		return false
	}
	return subtle.ConstantTimeCompare(a, b) == 1
}

// GenerateToken returns a 32-byte random base64url token (no padding).
func GenerateToken() string {
	b := make([]byte, 32)
	if _, err := rand.Read(b); err != nil {
		panic(fmt.Sprintf("crypto/rand failed: %v", err))
	}
	return base64.RawURLEncoding.EncodeToString(b)
}

// HashToken returns the SHA-256 hex digest stored in the sessions table.
func HashToken(token string) string {
	sum := sha256.Sum256([]byte(token))
	return hex.EncodeToString(sum[:])
}

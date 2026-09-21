package httpapi

import (
	"bytes"
	"encoding/json"
	"errors"
	"io"
	"net/http"

	"github.com/gin-gonic/gin"
)

// maxBodyBytes mirrors Fastify's bodyLimit (4MB) from app.js.
const maxBodyBytes = 4 * 1024 * 1024

// ok writes a 200 envelope with data only.
func ok(c *gin.Context, data any) {
	c.PureJSON(http.StatusOK, gin.H{"code": 200, "data": data})
}

// okMsg writes a 200 envelope with a message only.
func okMsg(c *gin.Context, msg string) {
	c.PureJSON(http.StatusOK, gin.H{"code": 200, "msg": msg})
}

// okMsgData writes a 200 envelope with both message and data.
func okMsgData(c *gin.Context, msg string, data any) {
	c.PureJSON(http.StatusOK, gin.H{"code": 200, "msg": msg, "data": data})
}

// fail writes the envelope with the HTTP status equal to the business code.
func fail(c *gin.Context, code int, msg string) {
	c.PureJSON(code, gin.H{"code": code, "msg": msg})
}

// decodeBody reads the request body into a generic map (mirroring JS `req.body`).
// It returns false and writes the error response when decoding fails.
func decodeBody(c *gin.Context) (map[string]any, bool) {
	body := map[string]any{}
	if c.Request == nil || c.Request.Body == nil {
		return body, true
	}
	c.Request.Body = http.MaxBytesReader(c.Writer, c.Request.Body, maxBodyBytes)
	raw, err := io.ReadAll(c.Request.Body)
	if err != nil {
		var mbe *http.MaxBytesError
		if errors.As(err, &mbe) {
			fail(c, http.StatusRequestEntityTooLarge, "请求体过大（上限 4MB）")
			return nil, false
		}
		fail(c, http.StatusBadRequest, err.Error())
		return nil, false
	}
	if len(bytes.TrimSpace(raw)) == 0 {
		return body, true
	}
	if err := json.Unmarshal(raw, &body); err != nil {
		fail(c, http.StatusBadRequest, "请求体不是合法的 JSON")
		return nil, false
	}
	return body, true
}

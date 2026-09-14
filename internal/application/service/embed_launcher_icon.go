package service

import (
	"encoding/base64"
	"errors"
	"fmt"
	"regexp"
)

// ErrEmbedLauncherIconInvalid marks a launcher icon that is not an allowed
// base64 image data URL or exceeds the decoded size cap.
var ErrEmbedLauncherIconInvalid = errors.New("invalid embed launcher icon")

// MaxEmbedLauncherIconBytes caps the decoded size of an embedded launcher icon.
const MaxEmbedLauncherIconBytes = 200 * 1024

var launcherIconDataURLPattern = regexp.MustCompile(`^data:image/(png|jpeg|svg\+xml|webp);base64,`)

// ValidateEmbedLauncherIcon enforces the data-URL format and decoded size cap.
// An empty string clears the icon and is always valid.
//
// SVG is accepted only because consumers render it via an <img> tag, which
// blocks script execution. Do not inline the SVG payload into the DOM.
func ValidateEmbedLauncherIcon(v string) error {
	if v == "" {
		return nil
	}
	loc := launcherIconDataURLPattern.FindStringIndex(v)
	if loc == nil || loc[0] != 0 {
		return fmt.Errorf("%w: must be a base64 data URL of png/jpeg/svg/webp", ErrEmbedLauncherIconInvalid)
	}
	decoded, err := base64.StdEncoding.DecodeString(v[loc[1]:])
	if err != nil {
		return fmt.Errorf("%w: invalid base64 payload", ErrEmbedLauncherIconInvalid)
	}
	if len(decoded) > MaxEmbedLauncherIconBytes {
		return fmt.Errorf("%w: image exceeds %d bytes", ErrEmbedLauncherIconInvalid, MaxEmbedLauncherIconBytes)
	}
	return nil
}

package service

import (
	"encoding/base64"
	"errors"
	"strings"
	"testing"
)

func iconDataURL(mediaType string, size int) string {
	return "data:" + mediaType + ";base64," + base64.StdEncoding.EncodeToString(make([]byte, size))
}

func TestValidateEmbedLauncherIcon(t *testing.T) {
	cases := []struct {
		name    string
		input   string
		wantErr bool
	}{
		{"empty clears icon", "", false},
		{"png ok", iconDataURL("image/png", 128), false},
		{"jpeg ok", iconDataURL("image/jpeg", 128), false},
		{"svg ok", iconDataURL("image/svg+xml", 128), false},
		{"webp ok", iconDataURL("image/webp", 128), false},
		{"gif rejected", iconDataURL("image/gif", 128), true},
		{"not a data url", "https://example.com/icon.png", true},
		{"bad base64", "data:image/png;base64,!!!not-base64!!!", true},
		{"too large", iconDataURL("image/png", MaxEmbedLauncherIconBytes+1), true},
		{"at limit", iconDataURL("image/png", MaxEmbedLauncherIconBytes), false},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			err := ValidateEmbedLauncherIcon(tc.input)
			if tc.wantErr && !errors.Is(err, ErrEmbedLauncherIconInvalid) {
				t.Fatalf("ValidateEmbedLauncherIcon(%q) = %v, want ErrEmbedLauncherIconInvalid", tc.input[:min(len(tc.input), 40)], err)
			}
			if !tc.wantErr && err != nil {
				t.Fatalf("ValidateEmbedLauncherIcon() error = %v, want nil", err)
			}
		})
	}
}

func TestValidateEmbedLauncherIconRejectsOversizeString(t *testing.T) {
	// 即使 base64 合法,超长字符串也应快速拒绝(解码后超限)
	huge := iconDataURL("image/png", MaxEmbedLauncherIconBytes*2)
	if err := ValidateEmbedLauncherIcon(huge); !errors.Is(err, ErrEmbedLauncherIconInvalid) {
		t.Fatalf("error = %v, want ErrEmbedLauncherIconInvalid", err)
	}
	if !strings.HasPrefix(huge, "data:image/png;base64,") {
		t.Fatal("test setup broken")
	}
}

package service

import (
	"context"
	"errors"
	"testing"

	"github.com/Tencent/WeKnora/internal/types"
)

// Create must persist LauncherIcon: the HTTP handler validates and forwards it,
// so dropping it here would silently discard a valid custom icon on the
// new-channel path.
func TestEmbedChannelCreatePersistsLauncherIcon(t *testing.T) {
	repo := &stubEmbedChannelRepo{}
	svc := &embedChannelService{
		repo: repo,
		agentService: &stubAgentForEmbed{
			agent: &types.CustomAgent{ID: "agent-1", TenantID: 42},
		},
	}

	icon := "data:image/png;base64,aWNvbg=="
	created, token, err := svc.Create(context.Background(), 42, "agent-1", &types.EmbedChannel{
		Name:         "Support",
		LauncherIcon: icon,
	})
	if err != nil {
		t.Fatalf("Create() error = %v", err)
	}
	if token == "" {
		t.Fatal("Create() returned empty token")
	}
	if created.LauncherIcon != icon {
		t.Fatalf("returned LauncherIcon = %q, want %q", created.LauncherIcon, icon)
	}
	if repo.created == nil {
		t.Fatal("repo.Create was not called")
	}
	if repo.created.LauncherIcon != icon {
		t.Fatalf("persisted LauncherIcon = %q, want %q", repo.created.LauncherIcon, icon)
	}
}

func TestEmbedChannelCreateRejectsInvalidLauncherIcon(t *testing.T) {
	repo := &stubEmbedChannelRepo{}
	svc := &embedChannelService{
		repo: repo,
		agentService: &stubAgentForEmbed{
			agent: &types.CustomAgent{ID: "agent-1", TenantID: 42},
		},
	}

	_, _, err := svc.Create(context.Background(), 42, "agent-1", &types.EmbedChannel{
		Name:         "Support",
		LauncherIcon: "https://evil/x.png",
	})
	if !errors.Is(err, ErrEmbedLauncherIconInvalid) {
		t.Fatalf("Create() error = %v, want ErrEmbedLauncherIconInvalid", err)
	}
	if repo.created != nil {
		t.Fatalf("repo.Create should not have been called, got %#v", repo.created)
	}
}

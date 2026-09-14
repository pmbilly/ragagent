package service

import (
	"context"
	"errors"
	"testing"

	"github.com/Tencent/WeKnora/internal/types"
	"github.com/Tencent/WeKnora/internal/types/interfaces"
)

type stubEmbedChannelRepo struct {
	interfaces.EmbedChannelRepository
	ch      *types.EmbedChannel
	created *types.EmbedChannel
}

func (r *stubEmbedChannelRepo) Create(_ context.Context, ch *types.EmbedChannel) error {
	cp := *ch
	r.created = &cp
	return nil
}

func (r *stubEmbedChannelRepo) GetByID(_ context.Context, id string) (*types.EmbedChannel, error) {
	if r.ch == nil || r.ch.ID != id {
		return nil, nil
	}
	cp := *r.ch
	return &cp, nil
}

func (r *stubEmbedChannelRepo) Update(_ context.Context, ch *types.EmbedChannel) error {
	cp := *ch
	r.ch = &cp
	return nil
}

func TestEmbedChannelUpdateAgentID(t *testing.T) {
	repo := &stubEmbedChannelRepo{
		ch: &types.EmbedChannel{
			ID:       "ch-1",
			TenantID: 42,
			AgentID:  "agent-old",
			Name:     "Support",
		},
	}
	svc := &embedChannelService{
		repo: repo,
		agentService: &stubAgentForEmbed{
			agent: &types.CustomAgent{ID: "agent-new", TenantID: 42},
		},
	}
	enabled := true
	updated, err := svc.Update(
		context.Background(),
		42,
		"ch-1",
		&types.EmbedChannel{AgentID: "agent-new"},
		&enabled, nil, nil, nil, nil, nil, nil, nil,
	)
	if err != nil {
		t.Fatalf("Update() error = %v", err)
	}
	if updated.AgentID != "agent-new" {
		t.Fatalf("updated.AgentID = %q, want agent-new", updated.AgentID)
	}
	if repo.ch.AgentID != "agent-new" {
		t.Fatalf("persisted AgentID = %q, want agent-new", repo.ch.AgentID)
	}
}

func TestEmbedChannelUpdateLauncherIcon(t *testing.T) {
	repo := &stubEmbedChannelRepo{
		ch: &types.EmbedChannel{ID: "ch-1", TenantID: 42, AgentID: "agent-1", Name: "Support"},
	}
	svc := &embedChannelService{repo: repo}

	// 不传指针(nil)= 不动该字段;先预置一个旧值验证不被清掉
	repo.ch.LauncherIcon = "data:image/png;base64,b2xk"
	updated, err := svc.Update(
		context.Background(), 42, "ch-1",
		&types.EmbedChannel{},
		nil, nil, nil, nil, nil, nil, nil, nil,
	)
	if err != nil {
		t.Fatalf("Update() error = %v", err)
	}
	if updated.LauncherIcon != "data:image/png;base64,b2xk" {
		t.Fatalf("LauncherIcon = %q, want unchanged", updated.LauncherIcon)
	}

	// 传入合法 data URL = 更新
	icon := "data:image/png;base64,bmV3"
	updated, err = svc.Update(
		context.Background(), 42, "ch-1",
		&types.EmbedChannel{},
		nil, nil, nil, nil, nil, nil, nil, &icon,
	)
	if err != nil {
		t.Fatalf("Update() error = %v", err)
	}
	if updated.LauncherIcon != icon {
		t.Fatalf("LauncherIcon = %q, want %q", updated.LauncherIcon, icon)
	}

	// 传入空串 = 清除
	empty := ""
	updated, err = svc.Update(
		context.Background(), 42, "ch-1",
		&types.EmbedChannel{},
		nil, nil, nil, nil, nil, nil, nil, &empty,
	)
	if err != nil {
		t.Fatalf("Update() error = %v", err)
	}
	if updated.LauncherIcon != "" {
		t.Fatalf("LauncherIcon = %q, want cleared", updated.LauncherIcon)
	}

	// 传入非法值 = 报错
	bad := "https://evil.example.com/x.png"
	if _, err = svc.Update(
		context.Background(), 42, "ch-1",
		&types.EmbedChannel{},
		nil, nil, nil, nil, nil, nil, nil, &bad,
	); !errors.Is(err, ErrEmbedLauncherIconInvalid) {
		t.Fatalf("Update() error = %v, want ErrEmbedLauncherIconInvalid", err)
	}
}

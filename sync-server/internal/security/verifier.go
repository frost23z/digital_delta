package security

import (
	"errors"

	digitaldeltav1 "github.com/frost23z/digital_delta/sync-server/internal/gen/proto"
)

type SignatureVerifier interface {
	VerifyMutation(originNode string, mutation *digitaldeltav1.Mutation) error
}

type NoopSignatureVerifier struct{}

func (NoopSignatureVerifier) VerifyMutation(_ string, mutation *digitaldeltav1.Mutation) error {
	if mutation == nil {
		return errors.New("mutation is required")
	}

	// Hook for step-8 signature verification; keep server optional for demo use.
	_ = mutation.GetSignature()
	_ = mutation.GetPayloadHash()
	return nil
}

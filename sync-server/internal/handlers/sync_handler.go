package handlers

import (
	"context"
	"fmt"
	"log"
	"sync"
	"time"

	digitaldeltav1 "github.com/frost23z/digital_delta/sync-server/internal/gen/proto"
	"github.com/frost23z/digital_delta/sync-server/internal/security"
	"github.com/frost23z/digital_delta/sync-server/internal/storage"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

type registeredNode struct {
	PublicKey    string
	Role         string
	RegisteredAt uint64
}

type SyncHandler struct {
	digitaldeltav1.UnimplementedSyncServiceServer

	mutations   *storage.MutationStore
	checkpoints *storage.CheckpointStore
	verifier    security.SignatureVerifier

	nodesMu sync.RWMutex
	nodes   map[string]registeredNode
	nowFn   func() time.Time
}

func NewSyncHandler(
	mutations *storage.MutationStore,
	checkpoints *storage.CheckpointStore,
	verifier security.SignatureVerifier,
) *SyncHandler {
	if verifier == nil {
		verifier = security.NoopSignatureVerifier{}
	}

	return &SyncHandler{
		mutations:   mutations,
		checkpoints: checkpoints,
		verifier:    verifier,
		nodes:       make(map[string]registeredNode),
		nowFn:       time.Now,
	}
}

func (h *SyncHandler) RegisterNode(
	_ context.Context,
	req *digitaldeltav1.RegisterNodeRequest,
) (*digitaldeltav1.RegisterNodeResponse, error) {
	if req == nil {
		log.Printf("[sync-grpc] RegisterNode invalid: nil request")
		return nil, status.Error(codes.InvalidArgument, "request is required")
	}
	nodeID := req.GetNodeId()
	if nodeID == "" {
		log.Printf("[sync-grpc] RegisterNode invalid: node_id is required")
		return nil, status.Error(codes.InvalidArgument, "node_id is required")
	}

	log.Printf("[sync-grpc] RegisterNode start node_id=%s role=%s", nodeID, req.GetRole())

	now := uint64(h.nowFn().Unix())
	registeredCount := 0
	h.nodesMu.Lock()
	h.nodes[nodeID] = registeredNode{
		PublicKey:    req.GetPublicKey(),
		Role:         req.GetRole(),
		RegisteredAt: now,
	}
	registeredCount = len(h.nodes)
	h.nodesMu.Unlock()

	if h.checkpoints.Get(nodeID) == nil {
		h.checkpoints.Save(nodeID, &digitaldeltav1.SyncCheckpoint{LastSeenCounterByDevice: map[string]uint64{}})
	}

	log.Printf("[sync-grpc] RegisterNode ok node_id=%s registered_nodes=%d", nodeID, registeredCount)

	return &digitaldeltav1.RegisterNodeResponse{
		Ok:         true,
		Reason:     "registered",
		ServerTime: now,
	}, nil
}

func (h *SyncHandler) DeltaSync(
	_ context.Context,
	req *digitaldeltav1.DeltaSyncRequest,
) (*digitaldeltav1.DeltaSyncResponse, error) {
	if req == nil {
		log.Printf("[sync-grpc] DeltaSync invalid: nil request")
		return nil, status.Error(codes.InvalidArgument, "request is required")
	}
	nodeID := req.GetNodeId()
	if nodeID == "" {
		log.Printf("[sync-grpc] DeltaSync invalid: node_id is required")
		return nil, status.Error(codes.InvalidArgument, "node_id is required")
	}

	checkpointEntries := 0
	if cp := req.GetCheckpoint(); cp != nil {
		checkpointEntries = len(cp.GetLastSeenCounterByDevice())
	}
	log.Printf(
		"[sync-grpc] DeltaSync start node_id=%s outgoing=%d checkpoint_entries=%d",
		nodeID,
		len(req.GetOutgoingMutations()),
		checkpointEntries,
	)

	for _, mutation := range req.GetOutgoingMutations() {
		if mutation == nil || mutation.GetMutationId() == "" {
			log.Printf("[sync-grpc] DeltaSync invalid mutation: missing mutation_id node_id=%s", nodeID)
			return nil, status.Error(codes.InvalidArgument, "each outgoing mutation must include mutation_id")
		}
		if err := h.verifier.VerifyMutation(nodeID, mutation); err != nil {
			log.Printf("[sync-grpc] DeltaSync signature verification failed node_id=%s mutation_id=%s err=%v", nodeID, mutation.GetMutationId(), err)
			return nil, status.Error(codes.InvalidArgument, fmt.Sprintf("signature verification failed for mutation %s: %v", mutation.GetMutationId(), err))
		}
	}

	effectiveCheckpoint := storage.MergeCheckpoints(
		h.checkpoints.Get(nodeID),
		req.GetCheckpoint(),
	)

	h.mutations.SaveOutgoing(nodeID, req.GetOutgoingMutations())

	incomingStored := h.mutations.UnseenSince(nodeID, effectiveCheckpoint)
	incomingMutations := make([]*digitaldeltav1.Mutation, 0, len(incomingStored))

	updatedCheckpoint := storage.MergeCheckpoints(effectiveCheckpoint)

	for _, outgoing := range req.GetOutgoingMutations() {
		counter := storage.MutationCounterForNode(outgoing, nodeID)
		storage.BumpCheckpointCounter(updatedCheckpoint, nodeID, counter)
	}

	for _, stored := range incomingStored {
		incomingMutations = append(incomingMutations, stored.Mutation)
		storage.BumpCheckpointCounter(updatedCheckpoint, stored.OriginNode, stored.Counter)
	}

	h.checkpoints.Save(nodeID, updatedCheckpoint)

	log.Printf(
		"[sync-grpc] DeltaSync ok node_id=%s outgoing=%d incoming=%d updated_checkpoint_entries=%d",
		nodeID,
		len(req.GetOutgoingMutations()),
		len(incomingMutations),
		len(updatedCheckpoint.GetLastSeenCounterByDevice()),
	)

	return &digitaldeltav1.DeltaSyncResponse{
		IncomingMutations: incomingMutations,
		UpdatedCheckpoint: updatedCheckpoint,
		HasConflicts:      false,
	}, nil
}

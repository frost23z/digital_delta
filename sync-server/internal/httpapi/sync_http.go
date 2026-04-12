package httpapi

import (
	"encoding/json"
	"log"
	"net/http"

	digitaldeltav1 "github.com/frost23z/digital_delta/sync-server/internal/gen/proto"
	"github.com/frost23z/digital_delta/sync-server/internal/handlers"
)

type registerNodeRequest struct {
	NodeID    string `json:"node_id"`
	PublicKey string `json:"public_key,omitempty"`
	Role      string `json:"role,omitempty"`
}

type deltaSyncRequest struct {
	NodeID            string                `json:"node_id"`
	Checkpoint        map[string]uint64     `json:"checkpoint,omitempty"`
	OutgoingMutations []httpMutationPayload `json:"outgoing_mutations"`
}

type httpMutationPayload struct {
	MutationID       string            `json:"mutation_id"`
	EntityType       string            `json:"entity_type"`
	EntityID         string            `json:"entity_id"`
	ChangedFields    map[string]string `json:"changed_fields,omitempty"`
	ChangedFieldsRaw string            `json:"changed_fields_json,omitempty"`
	ActorID          string            `json:"actor_id"`
	Timestamp        uint64            `json:"timestamp"`
	DeviceID         string            `json:"device_id,omitempty"`
	VectorClock      map[string]uint64 `json:"vector_clock,omitempty"`
}

type deltaSyncResponse struct {
	OK                bool                  `json:"ok"`
	Message           string                `json:"message,omitempty"`
	SyncedCount       int                   `json:"synced_count"`
	IncomingCount     int                   `json:"incoming_count"`
	IncomingMutations []httpMutationPayload `json:"incoming_mutations,omitempty"`
	SyncedMutationIDs []string              `json:"synced_mutation_ids,omitempty"`
	UpdatedCheckpoint map[string]uint64     `json:"updated_checkpoint,omitempty"`
	HasConflicts      bool                  `json:"has_conflicts"`
	ConflictEntityIDs []string              `json:"conflict_entity_ids,omitempty"`
}

func RegisterRoutes(mux *http.ServeMux, syncHandler *handlers.SyncHandler) {
	mux.HandleFunc("/health", func(w http.ResponseWriter, _ *http.Request) {
		writeJSON(w, http.StatusOK, map[string]any{
			"ok":      true,
			"service": "digital-delta-sync",
		})
	})

	mux.HandleFunc("/api/sync/register", func(w http.ResponseWriter, r *http.Request) {
		log.Printf("[sync-http] %s %s remote=%s", r.Method, r.URL.Path, r.RemoteAddr)

		if r.Method != http.MethodPost {
			writeJSON(w, http.StatusMethodNotAllowed, map[string]any{
				"ok":      false,
				"message": "method not allowed",
			})
			return
		}

		if syncHandler == nil {
			writeJSON(w, http.StatusInternalServerError, map[string]any{
				"ok":      false,
				"message": "sync handler unavailable",
			})
			return
		}

		var req registerNodeRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			log.Printf("[sync-http] register decode failed remote=%s err=%v", r.RemoteAddr, err)
			writeJSON(w, http.StatusBadRequest, map[string]any{
				"ok":      false,
				"message": "invalid json body",
			})
			return
		}

		log.Printf("[sync-http] register request node_id=%s role=%s", req.NodeID, req.Role)

		resp, err := syncHandler.RegisterNode(r.Context(), &digitaldeltav1.RegisterNodeRequest{
			NodeId:    req.NodeID,
			PublicKey: req.PublicKey,
			Role:      req.Role,
		})
		if err != nil {
			log.Printf("[sync-http] register failed node_id=%s err=%v", req.NodeID, err)
			writeJSON(w, http.StatusBadRequest, map[string]any{
				"ok":      false,
				"message": err.Error(),
			})
			return
		}

		log.Printf("[sync-http] register ok node_id=%s", req.NodeID)

		writeJSON(w, http.StatusOK, resp)
	})

	mux.HandleFunc("/api/sync/delta", func(w http.ResponseWriter, r *http.Request) {
		log.Printf("[sync-http] %s %s remote=%s", r.Method, r.URL.Path, r.RemoteAddr)

		if r.Method != http.MethodPost {
			writeJSON(w, http.StatusMethodNotAllowed, map[string]any{
				"ok":      false,
				"message": "method not allowed",
			})
			return
		}

		if syncHandler == nil {
			writeJSON(w, http.StatusInternalServerError, map[string]any{
				"ok":      false,
				"message": "sync handler unavailable",
			})
			return
		}

		var req deltaSyncRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			log.Printf("[sync-http] delta decode failed remote=%s err=%v", r.RemoteAddr, err)
			writeJSON(w, http.StatusBadRequest, map[string]any{
				"ok":      false,
				"message": "invalid json body",
			})
			return
		}

		log.Printf("[sync-http] delta request node_id=%s outgoing=%d", req.NodeID, len(req.OutgoingMutations))

		protoReq := &digitaldeltav1.DeltaSyncRequest{
			NodeId: req.NodeID,
			Checkpoint: &digitaldeltav1.SyncCheckpoint{
				LastSeenCounterByDevice: req.Checkpoint,
			},
			OutgoingMutations: make([]*digitaldeltav1.Mutation, 0, len(req.OutgoingMutations)),
		}

		syncedIDs := make([]string, 0, len(req.OutgoingMutations))
		for _, mutation := range req.OutgoingMutations {
			changedFields := mutation.ChangedFields
			if len(changedFields) == 0 && mutation.ChangedFieldsRaw != "" {
				if err := json.Unmarshal([]byte(mutation.ChangedFieldsRaw), &changedFields); err != nil {
					changedFields = map[string]string{"raw": mutation.ChangedFieldsRaw}
				}
			}

			vectorClock := mutation.VectorClock
			if len(vectorClock) == 0 {
				vectorClock = map[string]uint64{}
				sourceID := req.NodeID
				if sourceID == "" {
					sourceID = mutation.DeviceID
				}
				if sourceID != "" {
					vectorClock[sourceID] = mutation.Timestamp
				}
			}

			protoReq.OutgoingMutations = append(protoReq.OutgoingMutations, &digitaldeltav1.Mutation{
				MutationId:    mutation.MutationID,
				EntityType:    mutation.EntityType,
				EntityId:      mutation.EntityID,
				ChangedFields: changedFields,
				VectorClock: &digitaldeltav1.VectorClock{
					Counters: vectorClock,
				},
				ActorId:   mutation.ActorID,
				Timestamp: mutation.Timestamp,
			})
			syncedIDs = append(syncedIDs, mutation.MutationID)
		}

		protoResp, err := syncHandler.DeltaSync(r.Context(), protoReq)
		if err != nil {
			log.Printf("[sync-http] delta failed node_id=%s err=%v", req.NodeID, err)
			writeJSON(w, http.StatusBadRequest, map[string]any{
				"ok":      false,
				"message": err.Error(),
			})
			return
		}

		incomingPayloads := make([]httpMutationPayload, 0, len(protoResp.GetIncomingMutations()))
		for _, incoming := range protoResp.GetIncomingMutations() {
			if incoming == nil {
				continue
			}
			incomingPayloads = append(incomingPayloads, httpMutationPayload{
				MutationID:    incoming.GetMutationId(),
				EntityType:    incoming.GetEntityType(),
				EntityID:      incoming.GetEntityId(),
				ChangedFields: incoming.GetChangedFields(),
				ActorID:       incoming.GetActorId(),
				Timestamp:     incoming.GetTimestamp(),
				VectorClock:   incoming.GetVectorClock().GetCounters(),
			})
		}

		writeJSON(w, http.StatusOK, deltaSyncResponse{
			OK:                true,
			Message:           "sync completed",
			SyncedCount:       len(syncedIDs),
			IncomingCount:     len(protoResp.GetIncomingMutations()),
			IncomingMutations: incomingPayloads,
			SyncedMutationIDs: syncedIDs,
			UpdatedCheckpoint: protoResp.GetUpdatedCheckpoint().GetLastSeenCounterByDevice(),
			HasConflicts:      protoResp.GetHasConflicts(),
			ConflictEntityIDs: protoResp.GetConflictEntityIds(),
		})

		log.Printf(
			"[sync-http] delta ok node_id=%s synced=%d incoming=%d",
			req.NodeID,
			len(syncedIDs),
			len(protoResp.GetIncomingMutations()),
		)
	})
}

func writeJSON(w http.ResponseWriter, statusCode int, payload any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(statusCode)
	_ = json.NewEncoder(w).Encode(payload)
}

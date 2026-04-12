package storage

import (
	"database/sql"

	digitaldeltav1 "github.com/frost23z/digital_delta/sync-server/internal/gen/proto"
	"google.golang.org/protobuf/proto"
)

type CheckpointStore struct {
	db *sql.DB
}

func NewCheckpointStore(db *sql.DB) *CheckpointStore {
	return &CheckpointStore{
		db: db,
	}
}

func (s *CheckpointStore) Get(nodeID string) *digitaldeltav1.SyncCheckpoint {
	if s.db == nil || nodeID == "" {
		return &digitaldeltav1.SyncCheckpoint{LastSeenCounterByDevice: map[string]uint64{}}
	}

	var payload []byte
	err := s.db.QueryRow(`
SELECT checkpoint_payload
FROM sync_checkpoints
WHERE node_id = ?;
`, nodeID).Scan(&payload)
	if err == sql.ErrNoRows {
		return &digitaldeltav1.SyncCheckpoint{LastSeenCounterByDevice: map[string]uint64{}}
	}
	if err != nil {
		return &digitaldeltav1.SyncCheckpoint{LastSeenCounterByDevice: map[string]uint64{}}
	}

	stored := &digitaldeltav1.SyncCheckpoint{}
	if err := proto.Unmarshal(payload, stored); err != nil {
		return &digitaldeltav1.SyncCheckpoint{LastSeenCounterByDevice: map[string]uint64{}}
	}

	return cloneCheckpoint(stored)
}

func (s *CheckpointStore) Save(nodeID string, checkpoint *digitaldeltav1.SyncCheckpoint) {
	if s.db == nil || nodeID == "" || checkpoint == nil {
		return
	}

	payload, err := proto.Marshal(checkpoint)
	if err != nil {
		return
	}

	_, _ = s.db.Exec(`
INSERT INTO sync_checkpoints(node_id, checkpoint_payload, updated_at)
VALUES(?, ?, unixepoch())
ON CONFLICT(node_id) DO UPDATE SET
  checkpoint_payload = excluded.checkpoint_payload,
  updated_at = unixepoch();
`, nodeID, payload)
}

func MergeCheckpoints(checkpoints ...*digitaldeltav1.SyncCheckpoint) *digitaldeltav1.SyncCheckpoint {
	merged := &digitaldeltav1.SyncCheckpoint{LastSeenCounterByDevice: map[string]uint64{}}
	for _, checkpoint := range checkpoints {
		if checkpoint == nil {
			continue
		}
		for deviceID, counter := range checkpoint.GetLastSeenCounterByDevice() {
			if counter > merged.LastSeenCounterByDevice[deviceID] {
				merged.LastSeenCounterByDevice[deviceID] = counter
			}
		}
	}

	return merged
}

func BumpCheckpointCounter(checkpoint *digitaldeltav1.SyncCheckpoint, sourceNode string, counter uint64) {
	if checkpoint == nil || sourceNode == "" || counter == 0 {
		return
	}

	if checkpoint.LastSeenCounterByDevice == nil {
		checkpoint.LastSeenCounterByDevice = map[string]uint64{}
	}

	if counter > checkpoint.LastSeenCounterByDevice[sourceNode] {
		checkpoint.LastSeenCounterByDevice[sourceNode] = counter
	}
}

func cloneCheckpoint(checkpoint *digitaldeltav1.SyncCheckpoint) *digitaldeltav1.SyncCheckpoint {
	if checkpoint == nil {
		return &digitaldeltav1.SyncCheckpoint{LastSeenCounterByDevice: map[string]uint64{}}
	}

	cloned, ok := proto.Clone(checkpoint).(*digitaldeltav1.SyncCheckpoint)
	if !ok || cloned == nil {
		return &digitaldeltav1.SyncCheckpoint{LastSeenCounterByDevice: map[string]uint64{}}
	}

	if cloned.LastSeenCounterByDevice == nil {
		cloned.LastSeenCounterByDevice = map[string]uint64{}
	}

	return cloned
}
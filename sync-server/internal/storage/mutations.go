package storage

import (
	"database/sql"
	"fmt"

	digitaldeltav1 "github.com/frost23z/digital_delta/sync-server/internal/gen/proto"
	"google.golang.org/protobuf/proto"
)

type StoredMutation struct {
	OriginNode string
	Counter    uint64
	Mutation   *digitaldeltav1.Mutation
}

type MutationStore struct {
	db *sql.DB
}

func NewMutationStore(db *sql.DB) *MutationStore {
	return &MutationStore{db: db}
}

func (s *MutationStore) SaveOutgoing(originNode string, mutations []*digitaldeltav1.Mutation) int {
	if s.db == nil {
		return 0
	}

	stmt, err := s.db.Prepare(`
INSERT OR IGNORE INTO sync_mutations(
  mutation_id,
  origin_node,
  counter,
  payload
)
VALUES(?, ?, ?, ?);
`)
	if err != nil {
		return 0
	}
	defer stmt.Close()

	inserted := 0
	for _, mutation := range mutations {
		if mutation == nil {
			continue
		}

		mutationID := mutation.GetMutationId()
		if mutationID == "" {
			continue
		}

		payload, err := proto.Marshal(mutation)
		if err != nil {
			continue
		}

		counter := MutationCounterForNode(mutation, originNode)
		result, err := stmt.Exec(mutationID, originNode, int64(counter), payload)
		if err != nil {
			continue
		}

		affected, err := result.RowsAffected()
		if err == nil && affected > 0 {
			inserted++
		}
	}

	return inserted
}

func (s *MutationStore) UnseenSince(requestingNode string, checkpoint *digitaldeltav1.SyncCheckpoint) []StoredMutation {
	if s.db == nil {
		return []StoredMutation{}
	}

	lastSeen := map[string]uint64{}
	if checkpoint != nil {
		for nodeID, counter := range checkpoint.GetLastSeenCounterByDevice() {
			lastSeen[nodeID] = counter
		}
	}

	rows, err := s.db.Query(`
SELECT origin_node, counter, payload
FROM sync_mutations
WHERE origin_node <> ?
ORDER BY id ASC;
`, requestingNode)
	if err != nil {
		return []StoredMutation{}
	}
	defer rows.Close()

	unseen := make([]StoredMutation, 0)
	for rows.Next() {
		var (
			originNode string
			counter    int64
			payload    []byte
		)

		if err := rows.Scan(&originNode, &counter, &payload); err != nil {
			continue
		}

		if uint64(counter) <= lastSeen[originNode] {
			continue
		}

		mutation := &digitaldeltav1.Mutation{}
		if err := proto.Unmarshal(payload, mutation); err != nil {
			continue
		}

		unseen = append(unseen, StoredMutation{
			OriginNode: originNode,
			Counter:    uint64(counter),
			Mutation:   mutation,
		})
	}

	if err := rows.Err(); err != nil {
		return []StoredMutation{}
	}

	return unseen
}

func (s *MutationStore) Count() (int64, error) {
	if s.db == nil {
		return 0, fmt.Errorf("mutation store db is nil")
	}

	var count int64
	if err := s.db.QueryRow(`SELECT COUNT(*) FROM sync_mutations;`).Scan(&count); err != nil {
		return 0, err
	}

	return count, nil
}

func MutationCounterForNode(mutation *digitaldeltav1.Mutation, originNode string) uint64 {
	if mutation == nil {
		return 0
	}

	if originNode != "" {
		if vectorClock := mutation.GetVectorClock(); vectorClock != nil {
			if counter := vectorClock.GetCounters()[originNode]; counter > 0 {
				return counter
			}
		}
	}

	if mutation.GetTimestamp() > 0 {
		return mutation.GetTimestamp()
	}

	return 0
}

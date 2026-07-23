package command

import (
	"testing"

	clientModel "github.com/kakao/actionbase/internal/client/model"
)

func TestStorageToDatastoreURI(t *testing.T) {
	tests := []struct {
		name     string
		storage  clientModel.StorageEntity
		expected string
	}{
		{
			name: "HBASE uses namespace and tableName from conf",
			storage: clientModel.StorageEntity{
				Name: "default_hbase_storage",
				Type: "HBASE",
				Conf: map[string]any{"namespace": "kc_graph", "tableName": "edges"},
			},
			expected: "datastore://kc_graph/edges",
		},
		{
			name:     "JDBC maps to __jdbc__ sentinel namespace",
			storage:  clientModel.StorageEntity{Name: "metastore", Type: "JDBC", Conf: map[string]any{}},
			expected: "datastore://__jdbc__/metastore",
		},
		{
			name:     "LOCAL maps to __local__ sentinel namespace",
			storage:  clientModel.StorageEntity{Name: "local_backed_metastore", Type: "LOCAL", Conf: map[string]any{}},
			expected: "datastore://__local__/local_backed_metastore",
		},
		{
			name:     "unknown type falls back to the storage name unchanged",
			storage:  clientModel.StorageEntity{Name: "weird", Type: "NIL", Conf: map[string]any{}},
			expected: "weird",
		},
		{
			name: "HBASE with missing conf keys yields empty segments",
			storage: clientModel.StorageEntity{
				Name: "broken",
				Type: "HBASE",
				Conf: map[string]any{},
			},
			expected: "datastore:///",
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := storageToDatastoreURI(tt.storage); got != tt.expected {
				t.Errorf("storageToDatastoreURI(%+v) = %q, want %q", tt.storage, got, tt.expected)
			}
		})
	}
}

func TestIsSystem(t *testing.T) {
	tests := []struct {
		name     string
		input    string
		expected bool
	}{
		{"bare sys", "sys", true},
		{"origin-prefixed seed", "origin_default", true},
		{"operational database", "myservice", false},
		{"operational qualified label", "myservice.follows", false},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := isSystem(tt.input); got != tt.expected {
				t.Errorf("isSystem(%q) = %v, want %v", tt.input, got, tt.expected)
			}
		})
	}
}

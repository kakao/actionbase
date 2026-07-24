package command

import (
	"errors"
	"testing"

	clientModel "github.com/kakao/actionbase/internal/client/model"
)

func TestStorageToDatastoreURI(t *testing.T) {
	tests := []struct {
		name     string
		storage  clientModel.StorageEntity
		expected string
		wantErr  bool
	}{
		{
			name: "HBASE omits the namespace, keeping only the table",
			storage: clientModel.StorageEntity{
				Name: "default_hbase_storage",
				Type: "HBASE",
				Conf: map[string]any{"namespace": "kc_graph", "tableName": "edges"},
			},
			expected: "datastore:///edges",
		},
		{
			name: "HBASE without a namespace still converts",
			storage: clientModel.StorageEntity{
				Name: "no_ns",
				Type: "HBASE",
				Conf: map[string]any{"tableName": "edges"},
			},
			expected: "datastore:///edges",
		},
		{
			name:    "JDBC has no datastore equivalent",
			storage: clientModel.StorageEntity{Name: "metastore", Type: "JDBC", Conf: map[string]any{}},
			wantErr: true,
		},
		{
			name:    "LOCAL has no datastore equivalent",
			storage: clientModel.StorageEntity{Name: "local_backed_metastore", Type: "LOCAL", Conf: map[string]any{}},
			wantErr: true,
		},
		{
			name:    "unknown type has no datastore equivalent",
			storage: clientModel.StorageEntity{Name: "weird", Type: "NIL", Conf: map[string]any{}},
			wantErr: true,
		},
		{
			name: "HBASE with missing conf keys is rejected",
			storage: clientModel.StorageEntity{
				Name: "broken",
				Type: "HBASE",
				Conf: map[string]any{},
			},
			wantErr: true,
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, err := storageToDatastoreURI(tt.storage)
			if tt.wantErr {
				if err == nil {
					t.Errorf("storageToDatastoreURI(%+v) = %q, want error", tt.storage, got)
				}
				return
			}
			if err != nil {
				t.Errorf("storageToDatastoreURI(%+v) unexpected error: %v", tt.storage, err)
				return
			}
			if got != tt.expected {
				t.Errorf("storageToDatastoreURI(%+v) = %q, want %q", tt.storage, got, tt.expected)
			}
		})
	}
}

func TestTableBody(t *testing.T) {
	uriByName := map[string]string{"default_hbase_storage": "datastore:///edges"}
	errByName := map[string]error{"metastore": errors.New("storage type JDBC has no datastore:// equivalent")}

	t.Run("legacy storage name is rewritten to its URI", func(t *testing.T) {
		body, err := tableBody(clientModel.TableEntity{Storage: "default_hbase_storage"}, uriByName, errByName)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if body["storage"] != "datastore:///edges" {
			t.Errorf("storage = %q, want datastore:///edges", body["storage"])
		}
	})

	t.Run("datastore URI passes through unchanged", func(t *testing.T) {
		body, err := tableBody(clientModel.TableEntity{Storage: "datastore://ns/tbl"}, uriByName, errByName)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if body["storage"] != "datastore://ns/tbl" {
			t.Errorf("storage = %q, want datastore://ns/tbl", body["storage"])
		}
	})

	t.Run("unconvertible storage fails with its reason", func(t *testing.T) {
		if _, err := tableBody(clientModel.TableEntity{Storage: "metastore"}, uriByName, errByName); err == nil {
			t.Error("want error for unconvertible storage, got nil")
		}
	})

	t.Run("unknown storage name fails", func(t *testing.T) {
		if _, err := tableBody(clientModel.TableEntity{Storage: "ghost"}, uriByName, errByName); err == nil {
			t.Error("want error for unknown storage, got nil")
		}
	})

	t.Run("caches are carried over, nil becomes empty list", func(t *testing.T) {
		withCaches, err := tableBody(clientModel.TableEntity{
			Storage: "datastore://ns/tbl",
			Caches:  []any{map[string]any{"type": "recent"}},
		}, uriByName, errByName)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if caches, ok := withCaches["caches"].([]any); !ok || len(caches) != 1 {
			t.Errorf("caches = %v, want the original single entry", withCaches["caches"])
		}

		withoutCaches, err := tableBody(clientModel.TableEntity{Storage: "datastore://ns/tbl"}, uriByName, errByName)
		if err != nil {
			t.Fatalf("unexpected error: %v", err)
		}
		if caches, ok := withoutCaches["caches"].([]any); !ok || len(caches) != 0 {
			t.Errorf("caches = %v, want empty list", withoutCaches["caches"])
		}
	})
}

func TestIsAlreadyExists(t *testing.T) {
	tests := []struct {
		name     string
		status   int
		body     string
		expected bool
	}{
		{"v2 DDL duplicate: 400 with already-exists message", 400, `{"status":400,"message":"edge already exists"}`, true},
		{"precondition duplicate: 400 with name-already-exists message", 400, `{"message":"label name already exists : svc.tbl"}`, true},
		{"plain conflict status", 409, "", true},
		{"validation failure is not a duplicate", 400, `{"message":"desc is required"}`, false},
		{"server error is not a duplicate", 500, "edge already exists", false},
		{"success is not a duplicate", 201, "", false},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			if got := isAlreadyExists(tt.status, tt.body); got != tt.expected {
				t.Errorf("isAlreadyExists(%d, %q) = %v, want %v", tt.status, tt.body, got, tt.expected)
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

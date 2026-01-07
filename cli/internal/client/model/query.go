package model

type Counts struct {
	Counts []Count `json:"counts"`
	Count  int64   `json:"count"`
}

type Count struct {
	Start     any    `json:"start"`
	Direction string `json:"direction"`
	Count     int64  `json:"count"`
}

type Scan struct {
	Edges   []Edge `json:"edges"`
	Count   int64  `json:"count"`
	Total   int64  `json:"total"`
	Offset  string `json:"offset"`
	HasNext bool   `json:"hasNext"`
}

type Get struct {
	Edges   []Edge `json:"edges"`
	Count   int64  `json:"count"`
	Total   int64  `json:"total"`
	Offset  int64  `json:"offset"`
	HasNext bool   `json:"hasNext"`
}

type Edge struct {
	Version    int64                  `json:"version"`
	Source     any                    `json:"source"`
	Target     any                    `json:"target"`
	Properties map[string]interface{} `json:"properties"`
}

type Mutation struct {
	Results []Item
}

type Item struct {
	Source any    `json:"source"`
	Target any    `json:"target"`
	Status string `json:"status"`
	Count  int32  `json:"count"`
}

type EdgeBulkMutation struct {
	Mutations []MutationItem `json:"mutations"`
}

type MutationItem struct {
	Type string `json:"type"`
	Edge Edge   `json:"edge"`
}

type TableCreateRequest struct {
	Desc          string   `json:"desc"`
	Type          string   `json:"type"`
	Schema        *Schema  `json:"schema"`
	DirectionType string   `json:"dirType"`
	Storage       string   `json:"storage"`
	Groups        *[]Group `json:"groups,omitempty"`
	Indices       *[]Index `json:"indices,omitempty"`
	Event         bool     `json:"event"`
	ReadOnly      bool     `json:"readOnly"`
	Mode          string   `json:"mode"`
}

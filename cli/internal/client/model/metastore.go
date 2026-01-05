package model

type DdlStatus[T any] struct {
	Status  string  `json:"status"`
	Result  *T      `json:"result"`
	Message *string `json:"message"`
}

type DatabaseEntity struct {
	Active bool   `json:"active"`
	Name   string `json:"name"`
	Desc   string `json:"desc"`
}

type StorageEntity struct {
	Active bool                   `json:"active"`
	Name   string                 `json:"name"`
	Desc   string                 `json:"desc"`
	Type   string                 `json:"type"`
	Conf   map[string]interface{} `json:"conf"`
}

type TableEntity struct {
	Active   bool    `json:"active"`
	Name     string  `json:"name"`
	Desc     string  `json:"desc"`
	Type     string  `json:"type"`
	Schema   Schema  `json:"schema"`
	DirType  string  `json:"dirType"`
	Storage  string  `json:"storage"`
	Indices  []Index `json:"indices"`
	Groups   []Group `json:"groups"`
	Event    bool    `json:"event"`
	ReadOnly bool    `json:"readOnly"`
	Mode     string  `json:"mode"`
}

type Schema struct {
	Src    Field   `json:"src"`
	Tgt    Field   `json:"tgt"`
	Fields []Field `json:"fields"`
}

type Field struct {
	Type     string  `json:"type"`
	Desc     *string `json:"desc"`
	Name     *string `json:"name"`
	Nullable bool    `json:"nullable"`
}

type Index struct {
	Name   string       `json:"name"`
	Desc   string       `json:"desc"`
	Fields []IndexField `json:"fields"`
}

type IndexField struct {
	Name  string `json:"name"`
	Order string `json:"order"`
}

type Group struct {
	Group         string       `json:"group"`
	Type          string       `json:"type"`
	Fields        []GroupField `json:"fields"`
	ValueField    string       `json:"valueField"`
	Comment       string       `json:"comment"`
	DirectionType string       `json:"directionType"`
	Ttl           int64        `json:"ttl"`
}

type GroupField struct {
	Name   string  `json:"name"`
	Type   string  `json:"type"`
	Bucket *Bucket `json:"bucket"`
}

type Bucket struct {
	Type     string `json:"type"`
	Name     string `json:"name"`
	Unit     string `json:"unit"`
	Timezone string `json:"timezone"`
	Format   string `json:"format"`
}

type AliasEntity struct {
	Active bool         `json:"active"`
	Name   string       `json:"name"`
	Desc   string       `json:"desc"`
	Target string       `json:"target"`
	Table  *TableEntity `json:"label"`
}

type DdlPage[T any] struct {
	Count   int64 `json:"count"`
	Content []T   `json:"content"`
}

type StorageCreateRequest struct {
	Desc string                 `json:"desc"`
	Type string                 `json:"type"`
	Conf map[string]interface{} `json:"conf"`
}

type DatabaseCreateRequest struct {
	Desc string `json:"desc"`
}

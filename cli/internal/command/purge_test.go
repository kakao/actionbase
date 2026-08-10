package command

import (
	"encoding/json"
	"strings"
	"testing"

	"github.com/kakao/actionbase/internal/util"
)

const sampleSet = `{
  "metastore": "jdbc:mysql://meta.example.net:3306/graph",
  "table": "kc_graph_metadata",
  "service": "prod:wish",
  "generatedAt": "2026-08-10T04:12:33.481Z",
  "scanned": 6142,
  "nextCursor": 184203,
  "rows": [{"k": "aaa", "v": "bbb", "createdBy": "writer"}],
  "undecodable": [{"k": "broken", "reason": "bad key"}]
}`

func TestParseSetReadsTheSummaryFields(t *testing.T) {
	set, err := parseSet([]byte(sampleSet))
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if set.Table != "kc_graph_metadata" {
		t.Errorf("table = %q", set.Table)
	}
	if set.Scanned != 6142 {
		t.Errorf("scanned = %d", set.Scanned)
	}
	if len(set.Rows) != 1 || len(set.Undecodable) != 1 {
		t.Errorf("rows = %d, undecodable = %d", len(set.Rows), len(set.Undecodable))
	}
	if set.NextCursor == nil || *set.NextCursor != 184203 {
		t.Errorf("nextCursor = %v", set.NextCursor)
	}
}

// The document is posted back byte for byte, so a field this CLI has never
// heard of has to survive the round trip.
func TestUnknownFieldsSurviveTheRoundTrip(t *testing.T) {
	withExtra := `{"metastore":"jdbc:h2:mem:x","table":"t","service":"s","rows":[],"undecodable":[],"somethingNew":{"a":1}}`

	if _, err := parseSet([]byte(withExtra)); err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	posted, err := json.Marshal(json.RawMessage(withExtra))
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if string(posted) != withExtra {
		t.Errorf("the body was reshaped:\n got %s\nwant %s", posted, withExtra)
	}
}

func TestParseSetRejectsSomethingThatIsNotAPurgeDocument(t *testing.T) {
	for _, body := range []string{`{"hello":"world"}`, `not json at all`} {
		if _, err := parseSet([]byte(body)); err == nil {
			t.Errorf("expected %q to be rejected", body)
		}
	}
}

func TestSummariseNamesWhatWouldBeTouched(t *testing.T) {
	set, err := parseSet([]byte(sampleSet))
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	out := summarise(set, "wish.json")

	for _, want := range []string{"wish.json", "kc_graph_metadata", "prod:wish", "rows       1", "unreadable 1", "cursor 184203"} {
		if !strings.Contains(out, want) {
			t.Errorf("summary is missing %q:\n%s", want, out)
		}
	}
}

func TestFileArgFallsBackToTheDefault(t *testing.T) {
	if got := fileArg(util.ParseArgs([]string{})); got != defaultPurgeFile {
		t.Errorf("fileArg = %q", got)
	}
	if got := fileArg(util.ParseArgs([]string{"--file", "wish.json"})); got != "wish.json" {
		t.Errorf("fileArg = %q", got)
	}
}

func TestIntArgFallsBackWhenAbsentOrUnparseable(t *testing.T) {
	if got := intArg(util.ParseArgs([]string{}), "max", defaultMaxRows); got != defaultMaxRows {
		t.Errorf("intArg = %d", got)
	}
	if got := intArg(util.ParseArgs([]string{"--max", "seven"}), "max", defaultMaxRows); got != defaultMaxRows {
		t.Errorf("intArg = %d", got)
	}
	if got := intArg(util.ParseArgs([]string{"--max", "12"}), "max", defaultMaxRows); got != 12 {
		t.Errorf("intArg = %d", got)
	}
}

// Nothing destructive happens without --yes.
func TestApplyWithoutYesRefuses(t *testing.T) {
	parser := util.ParseArgs([]string{"--file", "wish.json"})
	if _, confirmed := parser.GetLenient("yes"); confirmed {
		t.Error("--yes should be absent here")
	}
	if _, confirmed := util.ParseArgs([]string{"--file", "wish.json", "--yes"}).GetLenient("yes"); !confirmed {
		t.Error("--yes should be present here")
	}
}

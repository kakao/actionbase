package command

import (
	"errors"
	"strconv"
	"strings"
	"testing"

	clientModel "github.com/kakao/actionbase/internal/client/model"
)

const (
	serviceLabel = 1773822659
	storageLabel = -1834823921
	tableLabel   = 300431312
	aliasLabel   = -641671585
)

func row(src, tgt string, labelID int64, active bool) clientModel.MetastoreDumpRow {
	return clientModel.MetastoreDumpRow{
		K: src + ":" + tgt,
		Decoded: &clientModel.MetastoreDecodedEdge{
			Active: active, Src: src, Tgt: tgt, LabelID: labelID,
		},
	}
}

// page builds n rows in one window. Only the walk's bounds care about the contents.
func page(n int, total int64, last bool) *clientModel.MetastoreDumpPage {
	rows := make([]clientModel.MetastoreDumpRow, 0, n)
	for i := 0; i < n; i++ {
		rows = append(rows, row("prod:wish", "t", tableLabel, true))
	}
	return &clientModel.MetastoreDumpPage{
		Content: rows, TotalElements: total, NumberOfElements: n, Last: last,
	}
}

func onlyWindow(t *testing.T, c *census) *window {
	t.Helper()
	if len(c.windows) != 1 {
		t.Fatalf("expected 1 window, got %d", len(c.windows))
	}
	for _, w := range c.windows {
		return w
	}
	return nil
}

func TestCensusCountsTombstonesTowardTheWindow(t *testing.T) {
	c := newCensus()
	c.add([]clientModel.MetastoreDumpRow{
		row("prod:wish", "likes", tableLabel, true),
		row("prod:wish", "gone", tableLabel, false),
		row("prod:wish", "also_gone", tableLabel, false),
	})

	w := onlyWindow(t, c)
	if w.total != 3 || w.tombstoned != 2 || w.live() != 1 {
		t.Errorf("total/tombstoned/live = %d/%d/%d, want 3/2/1", w.total, w.tombstoned, w.live())
	}
}

func TestCensusSeparatesWindowsBySourceAndLabel(t *testing.T) {
	c := newCensus()
	c.add([]clientModel.MetastoreDumpRow{
		row("prod:wish", "likes", tableLabel, true),
		row("prod:talk", "likes", tableLabel, true), // same label, other service
		row("prod:wish", "likes", aliasLabel, true), // same service, other label
	})

	if len(c.windows) != 3 {
		t.Fatalf("expected 3 windows, got %d", len(c.windows))
	}
}

func TestCensusFoldsAcrossPagesWithoutRetainingRows(t *testing.T) {
	c := newCensus()
	for i := 0; i < 5; i++ {
		rows := []clientModel.MetastoreDumpRow{
			row("prod:wish", "a", tableLabel, true),
			row("prod:wish", "b", tableLabel, false),
		}
		c.add(rows)
		// Overwriting the slice the caller handed in must not disturb the counters.
		for j := range rows {
			rows[j] = clientModel.MetastoreDumpRow{}
		}
	}

	w := onlyWindow(t, c)
	if c.rows != 10 || w.total != 10 || w.tombstoned != 5 {
		t.Errorf("rows/total/tombstoned = %d/%d/%d, want 10/10/5", c.rows, w.total, w.tombstoned)
	}
}

func TestCensusCountsUndecodableRowsSeparately(t *testing.T) {
	c := newCensus()
	c.add([]clientModel.MetastoreDumpRow{
		row("prod:wish", "likes", tableLabel, true),
		{K: "not-an-encoded-key", Decoded: nil},
	})

	if c.undecodable != 1 || c.badKey != "not-an-encoded-key" {
		t.Errorf("undecodable/badKey = %d/%q, want 1/not-an-encoded-key", c.undecodable, c.badKey)
	}
	if len(c.windows) != 1 {
		t.Errorf("windows = %d, want 1: an undecodable row joins none", len(c.windows))
	}
	if c.rows != 2 {
		t.Errorf("rows = %d, want 2: it was still read", c.rows)
	}
}

func TestCensusStopsGrowingAtTheWindowCap(t *testing.T) {
	c := newCensus()
	rows := make([]clientModel.MetastoreDumpRow, 0, maxRemainingWindows+10)
	for i := 0; i < maxRemainingWindows+10; i++ {
		rows = append(rows, row("prod:svc", "t", int64(i), true))
	}
	c.add(rows)

	if len(c.windows) != maxRemainingWindows {
		t.Errorf("windows = %d, want the cap %d", len(c.windows), maxRemainingWindows)
	}
	if c.dropped != 10 {
		t.Errorf("dropped = %d, want 10", c.dropped)
	}
	if c.rows != maxRemainingWindows+10 {
		t.Errorf("rows = %d, want every row read", c.rows)
	}
}

// printOrder is the report as a list of "kind src", which is the whole of what ordered() decides.
func printOrder(c *census) []string {
	var out []string
	windows, _ := c.ordered()
	for _, w := range windows {
		out = append(out, w.kind+" "+w.src)
	}
	return out
}

func TestCensusOrdersKnownKindsFirstThenFullestWindow(t *testing.T) {
	c := newCensus()
	c.add([]clientModel.MetastoreDumpRow{
		row("prod:wish", "a", aliasLabel, true),
		row("prod:small", "b", tableLabel, true),
		row("prod:big", "c", tableLabel, true),
		row("prod:big", "d", tableLabel, false),
		row("prod:origin", "e", storageLabel, true),
		row("prod:origin", "f", serviceLabel, true),
		row("prod:wish", "g", 424242, true),
	})

	want := []string{
		"service prod:origin",
		"storage prod:origin",
		"label prod:big", // two rows, so it leads the one-row window
		"label prod:small",
		"alias prod:wish",
		"labelId=424242 prod:wish", // unknown kinds sort after the known ones
	}
	if got := printOrder(c); strings.Join(got, ",") != strings.Join(want, ",") {
		t.Errorf("order =\n  %s\nwant\n  %s", strings.Join(got, "\n  "), strings.Join(want, "\n  "))
	}
}

func TestKindOfNamesTheKnownMetadataLabels(t *testing.T) {
	for labelID, want := range map[int64]string{
		serviceLabel: "service",
		storageLabel: "storage",
		tableLabel:   "label",
		aliasLabel:   "alias",
		12345:        "labelId=12345",
	} {
		if got := kindOf(labelID); got != want {
			t.Errorf("kindOf(%d) = %q, want %q", labelID, got, want)
		}
	}
}

func TestCensusKeepsANonStringSourcePrintable(t *testing.T) {
	c := newCensus()
	c.add([]clientModel.MetastoreDumpRow{{
		K:       "k",
		Decoded: &clientModel.MetastoreDecodedEdge{Active: true, Src: float64(42), Tgt: float64(7), LabelID: 3},
	}})

	if w := onlyWindow(t, c); w.src != "42" {
		t.Errorf("src = %q, want %q", w.src, "42")
	}
}

func TestWalkStopsWhenTheServerSaysThePageIsLast(t *testing.T) {
	requests := 0
	fetch := func(p, size int) (*clientModel.MetastoreDumpPage, error) {
		requests++
		return page(2, 4, p == 1), nil
	}

	c, stoppedBy, err := walk(fetch, remainingOptions{pageSize: 2, maxRows: 1000, maxPages: 100}, 4, nil)

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if stoppedBy != "" {
		t.Errorf("stoppedBy = %q, want empty: a complete walk hit no cap", stoppedBy)
	}
	if requests != 2 || c.rows != 4 {
		t.Errorf("requests/rows = %d/%d, want 2/4", requests, c.rows)
	}
}

func TestWalkStopsOnAnEmptyPageEvenIfLastNeverArrives(t *testing.T) {
	fetch := func(p, size int) (*clientModel.MetastoreDumpPage, error) {
		if p == 0 {
			return page(2, 100, false), nil
		}
		return page(0, 100, false), nil
	}

	c, stoppedBy, err := walk(fetch, remainingOptions{pageSize: 2, maxRows: 1000, maxPages: 100}, 100, nil)

	if err != nil || stoppedBy != "" || c.rows != 2 {
		t.Errorf("err/stoppedBy/rows = %v/%q/%d, want nil/empty/2", err, stoppedBy, c.rows)
	}
}

// The case the row cap alone does not cover: a server answering every request with one row and never
// admitting to being last. Without a page cap this is a request storm, and each of those requests
// costs the server a COUNT(*).
func TestWalkStopsAtThePageCapWhenPagesStayShort(t *testing.T) {
	requests := 0
	fetch := func(p, size int) (*clientModel.MetastoreDumpPage, error) {
		requests++
		return page(1, 1_000_000_000, false), nil
	}

	c, stoppedBy, err := walk(fetch, remainingOptions{pageSize: 500, maxRows: 500000, maxPages: 20}, 1_000_000_000, nil)

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if requests != 20 || c.rows != 20 {
		t.Errorf("requests/rows = %d/%d, want 20/20: the page cap, not the row cap", requests, c.rows)
	}
	if stoppedBy != "--max-pages 20" {
		t.Errorf("stoppedBy = %q, want the page cap named", stoppedBy)
	}
}

func TestWalkStopsAtTheRowCapWhenPagesAreFull(t *testing.T) {
	fetch := func(p, size int) (*clientModel.MetastoreDumpPage, error) {
		return page(size, 1_000_000_000, false), nil
	}

	c, stoppedBy, err := walk(fetch, remainingOptions{pageSize: 100, maxRows: 250, maxPages: 1000}, 1_000_000_000, nil)

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if c.rows != 300 {
		t.Errorf("rows = %d, want 300: the cap is checked between pages", c.rows)
	}
	if stoppedBy != "--max-rows 250" {
		t.Errorf("stoppedBy = %q, want the row cap named", stoppedBy)
	}
}

// The table's own row count bounds the walk, so a `last` that never comes cannot extend it past the
// end of the table.
func TestWalkStopsOnceItHasReadTheWholeTable(t *testing.T) {
	requests := 0
	fetch := func(p, size int) (*clientModel.MetastoreDumpPage, error) {
		requests++
		return page(size, 10, false), nil
	}

	c, stoppedBy, err := walk(fetch, remainingOptions{pageSize: 5, maxRows: 100000, maxPages: 1000}, 10, nil)

	if err != nil || stoppedBy != "" {
		t.Errorf("err/stoppedBy = %v/%q, want nil/empty", err, stoppedBy)
	}
	if requests != 2 || c.rows != 10 {
		t.Errorf("requests/rows = %d/%d, want 2/10", requests, c.rows)
	}
}

func TestWalkStopsOnInterruptAndKeepsWhatItRead(t *testing.T) {
	requests := 0
	fetch := func(p, size int) (*clientModel.MetastoreDumpPage, error) {
		requests++
		return page(2, 1_000_000, false), nil
	}

	c, stoppedBy, err := walk(fetch, remainingOptions{pageSize: 2, maxRows: 500000, maxPages: 1000}, 1_000_000, func() bool { return requests >= 3 })

	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if stoppedBy != "Ctrl-C" {
		t.Errorf("stoppedBy = %q, want Ctrl-C", stoppedBy)
	}
	if c.rows != 6 {
		t.Errorf("rows = %d, want the 6 rows read before the interrupt", c.rows)
	}
}

func TestWalkReturnsWhatItReadWhenAPageFails(t *testing.T) {
	fetch := func(p, size int) (*clientModel.MetastoreDumpPage, error) {
		if p == 0 {
			return page(2, 100, false), nil
		}
		return nil, errors.New("page 1: HTTP 500")
	}

	c, _, err := walk(fetch, remainingOptions{pageSize: 2, maxRows: 1000, maxPages: 100}, 100, nil)

	if err == nil {
		t.Fatal("expected the failure to surface")
	}
	if c.rows != 2 {
		t.Errorf("rows = %d, want the 2 rows already read", c.rows)
	}
}

func TestParseRemainingOptionsRequiresTheCapacity(t *testing.T) {
	_, failure := parseRemainingOptions(nil)
	if failure == nil {
		t.Fatal("a census with no --capacity should have been rejected")
	}
	if !strings.Contains(*failure.ErrorMessage, "graph.metadata-fetch-limit") {
		t.Errorf("error = %q, want it to name the server property", *failure.ErrorMessage)
	}
}

func TestParseRemainingOptionsDefaultsAndRejects(t *testing.T) {
	opts, failure := parseRemainingOptions([]string{"--capacity", "2000"})
	if failure != nil {
		t.Fatalf("defaults should parse: %v", *failure.ErrorMessage)
	}
	if opts.capacity != 2000 {
		t.Errorf("opts = %+v, want capacity 2000", opts)
	}
	if opts.pageSize != defaultRemainingPageSize || opts.maxPages != defaultRemainingMaxPages {
		t.Errorf("defaults = %+v, want page size %d and max pages %d",
			opts, defaultRemainingPageSize, defaultRemainingMaxPages)
	}

	for _, args := range [][]string{
		{"--capacity", "2000", "--page-size", "0"},
		{"--capacity", "2000", "--page-size", strconv.Itoa(maxRemainingPageSize + 1)},
		{"--capacity", "2000", "--max-pages", "0"},
		{"--capacity", "2000", "--max-rows", "-1"},
		{"--capacity", "abc"},
	} {
		if _, failure := parseRemainingOptions(args); failure == nil {
			t.Errorf("%v should have been rejected", args)
		}
	}
}

func TestNamespaceOfSplitsTheOwnerOffTheSource(t *testing.T) {
	for src, want := range map[string]string{
		"stg:origin":     "stg",
		"crm:aircrm":     "crm",
		"a:b:c":          "a",
		"no-separator":   "no-separator",
		":leading-colon": "",
	} {
		if got := namespaceOf(src); got != want {
			t.Errorf("namespaceOf(%q) = %q, want %q", src, got, want)
		}
	}
}

// A shared metastore table holds every namespace pointed at it, so the census has to keep them
// apart. This is the shape of `ab_default`, where several deployments read one table.
func sharedTable() *census {
	c := newCensus()
	rows := []clientModel.MetastoreDumpRow{}
	for i := 0; i < 30; i++ {
		rows = append(rows, row("stg:origin", "s"+strconv.Itoa(i), storageLabel, false))
	}
	for i := 0; i < 8; i++ {
		rows = append(rows, row("crm:aircrm", "t"+strconv.Itoa(i), tableLabel, true))
	}
	for i := 0; i < 2; i++ {
		rows = append(rows, row("bmt:origin", "s"+strconv.Itoa(i), storageLabel, true))
	}
	c.add(rows)
	return c
}

func TestCensusOrdersBiggestNamespaceFirst(t *testing.T) {
	c := sharedTable()

	windows, totals := c.ordered()
	if len(totals) != 3 || totals["stg"].rows != 30 || totals["stg"].windows != 1 {
		t.Fatalf("totals = %+v, want 3 namespaces and stg holding 30 rows in 1 window", totals)
	}

	var got []string
	for _, w := range windows {
		got = append(got, w.namespace)
	}
	if want := "stg,crm,bmt"; strings.Join(got, ",") != want {
		t.Errorf("namespaces = %v, want %s", got, want)
	}
}

// Every window prints, not just the fullest of each kind. The report used to decide what mattered
// and hid a window holding half the capacity behind one holding a little more.
func TestReportPrintsEveryWindow(t *testing.T) {
	c := sharedTable()

	got := report(c, remainingOptions{capacity: 2000}, 45, "")
	if !got.IsSuccess {
		t.Fatalf("nothing is over 2000: %v", *got.ErrorMessage)
	}
	if !strings.Contains(*got.Result, "3 windows, 3 namespaces") {
		t.Errorf("summary = %q, want every window and namespace counted", *got.Result)
	}
}

func TestReportFailsOnAnOverCapacityWindow(t *testing.T) {
	got := report(sharedTable(), remainingOptions{capacity: 10}, 45, "")
	if got.IsSuccess {
		t.Fatal("a 30-row window against a capacity of 10 should fail")
	}
	if !strings.Contains(*got.ErrorMessage, "over-capacity") {
		t.Errorf("error = %q, want it to name the truncation", *got.ErrorMessage)
	}
}

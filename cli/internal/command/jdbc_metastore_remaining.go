package command

import (
	"fmt"
	"net/http"
	"os"
	"os/signal"
	"sort"
	"strconv"
	"strings"
	"syscall"

	"github.com/kakao/actionbase/internal/client"
	clientModel "github.com/kakao/actionbase/internal/client/model"
	"github.com/kakao/actionbase/internal/command/model"
	"github.com/kakao/actionbase/internal/util"
)

const (
	// The OSS default for the server's metadataFetchLimit. A metadata scan asks the metastore for that
	// many rows and filters inactive ones out afterwards, so tombstones spend the budget a live scan
	// needs. Past it, a page is truncated and nothing says so.
	//
	// Named here only to say so in the error when --limit is missing. It is deliberately not a default
	// for --limit: a deployment overrides it with graph.metadata-fetch-limit and the server does not
	// expose the value - actuator masks it - so guessing produces confident, wrong verdicts against
	// exactly the servers worth asking about.
	ossMetadataFetchLimit = 1000

	// Large on purpose, and the knob that decides what the server pays. Every page request runs a
	// full COUNT(*) on top of its own LIMIT/OFFSET query, and the OFFSET cost grows with depth, so a
	// table is far cheaper in few large pages than in many small ones. The census retains nothing per
	// row, so the page only has to decode comfortably.
	defaultRemainingPageSize = 500
	maxRemainingPageSize     = 1000

	// Two caps, because they bound different things. Rows cover a table larger than expected; pages
	// cover what rows cannot - a server answering with short pages that never say they are last would
	// advance the row count a row at a time, with a COUNT(*) behind every request.
	defaultRemainingMaxRows  = 500000
	defaultRemainingMaxPages = 2000

	// The census holds one counter per scan window. More distinct windows than this is not a table
	// this command can summarise, so it stops growing rather than grow without bound.
	maxRemainingWindows = 10000
)

// metadataLabels says which metadata a scan of each label reads, in the order the report prints
// them. Declaration order is the report order, so there is one list rather than a map and a parallel
// slice that could disagree.
//
// A label's id is ValueUtils.stringHash(fullQualifiedName) - xxhash32 with seed 0 over a fixed name
// - so these are constants rather than something to recompute here. Printed from the engine, and
// they hold on 0.4.x too, which shares the names, the seed and the derivation.
var metadataLabels = []struct {
	id   int64
	name string
}{
	{1773822659, "service"},
	{-1834823921, "storage"},
	{300431312, "label"},
	{-641671585, "alias"},
	{-1531313765, "info"},
	{1397921084, "online_meta"},
	{-1296613462, "nil"},
}

// window is one metadata scan prefix: everything a single getAll(name) reads. getAll builds its scan
// key from encodeHashEdgeKeyPrefix(src, labelId) and the limit applies per key, so (src, labelId) is
// exactly the unit the limit bounds. Both come off the server's own decode of each row.
type window struct {
	kind       string
	namespace  string
	src        string
	total      int
	tombstoned int
}

func (w window) live() int { return w.total - w.tombstoned }

type windowKey struct {
	src     string
	labelID int64
}

// census is fixed-size state. Rows are folded in and dropped; none are retained.
type census struct {
	windows     map[windowKey]*window
	rows        int
	undecodable int
	badKey      string
	dropped     int
}

func newCensus() *census {
	return &census{windows: make(map[windowKey]*window)}
}

func (c *census) add(rows []clientModel.MetastoreDumpRow) {
	for i := range rows {
		c.rows++
		decoded := rows[i].Decoded
		if decoded == nil {
			// The server could not read it. It still occupies a scan window, but there is no way to
			// say which one.
			c.undecodable++
			if c.badKey == "" {
				c.badKey = rows[i].K
			}
			continue
		}

		key := windowKey{src: fmt.Sprintf("%v", decoded.Src), labelID: decoded.LabelID}
		w := c.windows[key]
		if w == nil {
			if len(c.windows) >= maxRemainingWindows {
				c.dropped++
				continue
			}
			w = &window{kind: kindOf(decoded.LabelID), namespace: namespaceOf(key.src), src: key.src}
			c.windows[key] = w
		}
		w.total++
		if !decoded.Active {
			w.tombstoned++
		}
	}
}

// namespaceOf splits the owner off the front of a scan source. Sources are `namespace:service`, and
// the namespace is the tenant that owns the metadata - which matters because a metastore table can
// be shared: several deployments point at one table and each sees the others' rows. Reporting
// without it hands an operator another tenant's window as if it were theirs.
//
// A source with no separator is its own namespace rather than an error. The report is a diagnostic;
// an unfamiliar source shape should still be counted and shown.
func namespaceOf(src string) string {
	namespace, _, _ := strings.Cut(src, ":")
	return namespace
}

// namespaceTotal is what a namespace's heading says, which has to be known before its first window
// is printed - and is also what orders the namespaces.
type namespaceTotal struct{ rows, windows int }

// ordered is the report, in print order: namespaces biggest first, known kinds in their declared
// order and the rest after by name, fullest window first within a kind.
//
// One sorted slice rather than a tree of groups. Every window prints, so the report is a single walk
// down this list that starts a heading whenever the namespace changes.
func (c *census) ordered() ([]*window, map[string]namespaceTotal) {
	totals := make(map[string]namespaceTotal)
	out := make([]*window, 0, len(c.windows))
	for _, w := range c.windows {
		t := totals[w.namespace]
		t.rows += w.total
		t.windows++
		totals[w.namespace] = t
		out = append(out, w)
	}

	sort.Slice(out, func(i, j int) bool {
		a, b := out[i], out[j]
		switch {
		case a.namespace != b.namespace && totals[a.namespace].rows != totals[b.namespace].rows:
			return totals[a.namespace].rows > totals[b.namespace].rows
		case a.namespace != b.namespace:
			return a.namespace < b.namespace
		case kindRank(a.kind) != kindRank(b.kind):
			return kindRank(a.kind) < kindRank(b.kind)
		case a.kind != b.kind:
			return a.kind < b.kind
		case a.total != b.total:
			return a.total > b.total
		default:
			return a.src < b.src
		}
	})
	return out, totals
}

// kindRank orders the known kinds ahead of anything unrecognised, which sorts after them by name.
func kindRank(kind string) int {
	for i, known := range metadataLabels {
		if known.name == kind {
			return i
		}
	}
	return len(metadataLabels)
}

func kindOf(labelID int64) string {
	for _, known := range metadataLabels {
		if known.id == labelID {
			return known.name
		}
	}
	return fmt.Sprintf("labelId=%d", labelID)
}

type JdbcMetastoreRemaining struct {
	client *client.ActionbaseClient
}

func NewJdbcMetastoreRemaining(c *client.ActionbaseClient) *JdbcMetastoreRemaining {
	return &JdbcMetastoreRemaining{client: c}
}

type remainingOptions struct {
	capacity int
	pageSize int
	maxRows  int
	maxPages int
}

func (m *JdbcMetastoreRemaining) Execute(args []string) *model.Response {
	if len(args) < 1 || args[0] != "remaining" {
		return model.Fail(fmt.Sprintf("Usage: %s", m.GetType().GetCommand()))
	}

	opts, failure := parseRemainingOptions(args[1:])
	if failure != nil {
		return failure
	}

	// One cheap page first, both to learn the table size and to find out whether this server serves
	// the endpoint at all. It was removed after 0.4.x, so a 404 is an answer, not a failure.
	probe := m.client.DumpMetastore(0, 1)
	switch {
	case probe.StatusCode == http.StatusNotFound:
		return model.Fail(fmt.Sprintf(
			"No %s on this server (0.4.x only), so this cannot be measured here.",
			client.MetastoreDumpPath))
	case probe.StatusCode == http.StatusUnauthorized:
		return model.Fail("Unauthorized. Set --authKey or ACT_API_KEY.")
	case probe.IsError():
		return model.Fail(fmt.Sprintf("Failed to read the JDBC metastore: %s", describeError(probe)))
	}

	totalRows := probe.Body.TotalElements
	// One line, because an operator reading it already knows what a metastore is. The page count is
	// here because each page costs the server a COUNT(*).
	util.Println(fmt.Sprintf("%d rows, capacity %d/window, pages of %d (max %d). Ctrl-C reports partial.",
		totalRows, opts.capacity, opts.pageSize, opts.maxPages))

	stop, stopped := interruptible()
	defer stopped()

	c, stoppedBy, err := walk(m.fetch, opts, totalRows, stop)
	if err != nil {
		return model.Fail(fmt.Sprintf("Failed to read the JDBC metastore: %s", err))
	}

	return report(c, opts, totalRows, stoppedBy)
}

func (m *JdbcMetastoreRemaining) fetch(page, size int) (*clientModel.MetastoreDumpPage, error) {
	resp := m.client.DumpMetastore(page, size)
	if resp.IsError() {
		return nil, fmt.Errorf("page %d: %s", page, describeError(resp))
	}
	return resp.Body, nil
}

func parseRemainingOptions(args []string) (remainingOptions, *model.Response) {
	parser := util.ParseArgs(args)
	opts := remainingOptions{
		pageSize: defaultRemainingPageSize,
		maxRows:  defaultRemainingMaxRows,
		maxPages: defaultRemainingMaxPages,
	}

	// Required rather than defaulted. Every verdict here is a comparison against this number, and the
	// server will not say what its own is, so a default would decide the answer while looking like a
	// detail.
	if _, ok := parser.Get("capacity"); !ok {
		return opts, model.Fail(fmt.Sprintf(
			"--capacity is required: the server's graph.metadata-fetch-limit, which it does not expose"+
				" (actuator masks it). OSS default %d.", ossMetadataFetchLimit))
	}

	for _, flag := range []struct {
		name string
		into *int
		max  int
	}{
		{"capacity", &opts.capacity, 0},
		{"page-size", &opts.pageSize, maxRemainingPageSize},
		{"max-rows", &opts.maxRows, 0},
		{"max-pages", &opts.maxPages, 0},
	} {
		v, ok := parser.Get(flag.name)
		if !ok {
			continue
		}
		parsed, err := strconv.Atoi(v)
		if err != nil || parsed < 1 || (flag.max > 0 && parsed > flag.max) {
			bound := "a positive integer"
			if flag.max > 0 {
				bound = fmt.Sprintf("between 1 and %d", flag.max)
			}
			return opts, model.Fail(fmt.Sprintf("--%s must be %s", flag.name, bound))
		}
		*flag.into = parsed
	}

	return opts, nil
}

// interruptible reports whether Ctrl-C has been pressed, and stops listening when done. The walk
// checks it once per page and returns on the first true, so there is nothing to remember - it stops
// and reports what it has, which beats losing the census to a killed process. Nothing here writes,
// so there is nothing to unwind.
func interruptible() (aborted func() bool, stop func()) {
	signals := make(chan os.Signal, 1)
	signal.Notify(signals, os.Interrupt, syscall.SIGTERM)
	return func() bool {
			select {
			case <-signals:
				return true
			default:
				return false
			}
		}, func() {
			signal.Stop(signals)
		}
}

type fetchPage func(page, size int) (*clientModel.MetastoreDumpPage, error)

// walk pages to the end of the table, folding each page into the census and dropping it.
//
// Five ways to stop, and it needs all five. A last page or an empty one are the ordinary two. The
// row and page caps are the two ceilings, and the page cap is the one that matters against a server
// handing back short pages that never say they are last. Reading as many rows as the table claims to
// hold ends it without trusting the server's own `last`. Whichever cap fired is named, so an
// incomplete census never reads as a complete one.
func walk(fetch fetchPage, opts remainingOptions, totalRows int64, aborted func() bool) (*census, string, error) {
	c := newCensus()
	for page := 0; ; page++ {
		switch {
		case aborted != nil && aborted():
			return c, "Ctrl-C", nil
		case page >= opts.maxPages:
			return c, fmt.Sprintf("--max-pages %d", opts.maxPages), nil
		case c.rows >= opts.maxRows:
			return c, fmt.Sprintf("--max-rows %d", opts.maxRows), nil
		}

		body, err := fetch(page, opts.pageSize)
		if err != nil {
			return c, "", err
		}
		c.add(body.Content)

		if body.Last || len(body.Content) == 0 || (totalRows > 0 && int64(c.rows) >= totalRows) {
			return c, "", nil
		}
	}
}

func report(c *census, opts remainingOptions, totalRows int64, stoppedBy string) *model.Response {
	capacity := opts.capacity
	windows, totals := c.ordered()
	over, tight := 0, 0

	// Every window, always. Printing only the fullest of each kind plus whatever crossed a threshold
	// meant the report decided what mattered, and it decided wrong: a window holding half the
	// capacity sat behind one holding a little more and never appeared. The list is short - a table
	// here has tens of windows, not thousands - and the cap on the census bounds it.
	namespace, kind := "", ""
	for _, w := range windows {
		if w.total > capacity {
			over++
		} else if w.total*10 >= capacity*9 {
			tight++ // within a tenth of capacity
		}

		if w.namespace != namespace {
			namespace, kind = w.namespace, ""
			t := totals[namespace]
			util.Println(fmt.Sprintf("%-10s %5d rows  %s", namespace, t.rows, plural(t.windows, "window")))
		}
		label := ""
		if w.kind != kind {
			kind, label = w.kind, w.kind
		}
		util.Println(fmt.Sprintf("  %-12s %-11s %-22s %5d/%-5d live %-6d tomb %d",
			label, room(w, capacity), w.src, w.total, capacity, w.live(), w.tombstoned))
	}

	if c.undecodable > 0 {
		// Flush left rather than indented with a namespace's windows: an undecodable row has no
		// namespace to belong to, so showing it inside one would read as a claim about that namespace.
		util.Println(fmt.Sprintf("%d undecodable (window unknown, e.g. k=%q)", c.undecodable, c.badKey))
	}
	if c.dropped > 0 {
		util.Println(fmt.Sprintf("%d rows uncounted, over %d windows", c.dropped, maxRemainingWindows))
	}

	summary := fmt.Sprintf("%d/%d rows, %s, %s.", c.rows, totalRows,
		plural(len(c.windows), "window"), plural(len(totals), "namespace"))
	switch {
	case stoppedBy != "":
		return model.Fail(fmt.Sprintf("%s Stopped at %s - incomplete, %d over is a lower bound.",
			summary, stoppedBy, over))
	case over > 0:
		return model.Fail(fmt.Sprintf("%s %d over, %d near. An over-capacity window is already"+
			" truncated and does not say so.", summary, over, tight))
	default:
		return model.SuccessWithResult(fmt.Sprintf("%s 0 over, %d near.", summary, tight))
	}
}

func plural(n int, noun string) string {
	if n == 1 {
		return fmt.Sprintf("1 %s", noun)
	}
	return fmt.Sprintf("%d %ss", n, noun)
}

func room(w *window, capacity int) string {
	if w.total > capacity {
		return fmt.Sprintf("OVER by %d", w.total-capacity)
	}
	return fmt.Sprintf("%d left", capacity-w.total)
}

func describeError[T any](r *client.Response[T]) string {
	if r.Error != nil {
		return r.Error.Error()
	}
	return fmt.Sprintf("HTTP %d", r.StatusCode)
}

func (m *JdbcMetastoreRemaining) GetDescription() string {
	return "Report how much room each metadata scan window has left in the JDBC metastore"
}

func (m *JdbcMetastoreRemaining) GetType() Type {
	return TypeJdbcMetastore
}

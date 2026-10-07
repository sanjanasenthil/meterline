# Meterline — Usage Metering & Billing Engine

**A 5-week portfolio project at SWE intern level, aimed at backend/infrastructure roles broadly rather than one company.**

---

## Part 1 — The project, in plain language

### The situation

More and more software is billed by what you actually use, not by a flat monthly fee. API calls, gigabytes stored, GPU-seconds, messages sent, rows scanned. Every AI company, every cloud provider, and most modern SaaS charges this way now.

That means somewhere inside those companies, a system is counting. Billions of small events go in one end, and at the end of the month an invoice comes out the other end that says a customer owes $4,182.19.

### The problem

Nobody checks that the number is right.

The counting system and the billing system are usually different systems, built at different times, by different teams. Events get dropped during traffic spikes. Events get counted twice because a client retried after a timeout. A price changes on the 14th and half the month bills at the wrong rate. The monthly job crashes at customer 4,000 of 10,000 and someone reruns it, and now some customers are billed twice.

None of this sets off an alarm. An invoice that is 3% too low looks exactly like an invoice that is correct. The customer is not going to call and tell you that you undercharged them.

It's like a grocery store where the scanner misses one item in a hundred. Nothing looks broken. The receipt prints, the customer pays, everyone goes home. You only find out at the end of the year when the inventory doesn't match the register.

### What you build

A metering and billing engine whose defining feature is that **it checks its own work.**

Usage events come in. They get deduplicated, aggregated, priced, and turned into invoices. And then a separate job independently re-adds the raw events and compares the total against the invoice that was issued. If they disagree, it fails loudly, names the customer, and shows you the difference.

On top of that: click any line on any invoice and see the exact list of raw events that produced it.

### The part that matters

The pipeline is **not** the project. Ingest, aggregate, price, invoice — that's four ordinary steps and a hundred applicants will build them. **Reconciliation and drill-down are the project.** They're the parts that turn a number into a number you can defend, and they're the parts nobody builds until after the first angry customer email.

---

## Part 2 — Why this problem

### The invariant

> Every usage event that enters the system appears in exactly one invoice line, at the correct price, exactly once. The sum of the raw events for a customer over a period equals the invoice total, to the cent.

One sentence. Everything in the architecture exists to defend it. When an interviewer asks "what does your project do," this is the answer, and it's better than a feature list.

### Cost of the status quo (sourced)

| Figure | Source |
| :---- | :---- |
| Subscription businesses lose an estimated 3–9% of revenue to billing leakage, with usage-based models at the worse end of that range (4–9%) | MGI Research 2024, aggregated with Vayu 2025 / Clari 2024 by leaksshield.com, 2026 |
| A 0.1% metering error on a $10M usage stream is $10K/year; at 1% it's $100K | leaksshield.com, 2026 |
| Roughly 42% of CFOs describe leakage as systematic rather than occasional — a structural property of how billing systems interact | EY Revenue Assurance study, 2024 |
| Detection requires periodically comparing metering-system counts against billing-system counts | Lago, *Revenue Leakage in SaaS*, 2026 |

**A note on source quality, because you will be asked.** These figures come from vendors who sell billing software, which is a real conflict of interest. Say so before someone else does. The defensible version of the claim is narrow and still strong: *usage-based billing loses money in ways that are structurally invisible, and the industry's own recommended fix is exactly the reconciliation job I built.* That last clause is the point — the whitepapers recommend reconciliation, and you implemented it.

### Who has this problem

Deliberately not a single-company pitch. The point of this project is that the problem shape is everywhere:

| Category | Examples | Why it applies |
| :---- | :---- | :---- |
| **AI / LLM providers** | OpenAI, Anthropic, Together | Per-token billing at enormous volume; tiny error rates are large dollars |
| **Cloud & infra** | AWS, Cloudflare, Vercel | The canonical metered-billing problem, at planetary scale |
| **Developer tools** | Stripe, Twilio, Datadog, Snowflake | Public docs on meter events, idempotency keys, and reconciliation — you can read how they do it |
| **Fintech / payments** | Any payment processor | Same invariant, higher stakes; idempotency is table stakes |

This satisfies the rule that a problem found at many targets is a project and a problem found at one is a distraction. You can walk into any backend interview and the interviewer's company either has this system or buys it.

### The unfair advantage: the reference implementations are public

Stripe documents its metering design openly. Their guidance is to use idempotency keys so that latency and retries don't cause usage to be reported more than once, with every meter event carrying an identifier you can specify yourself. The broader pattern is to give every usage event a deterministic id derived from the business action, enforce uniqueness on it, treat raw usage as immutable, correct with adjustments rather than edits, and run a daily job comparing internal totals per customer per meter per day against the billing system's state.

This is a gift. You are not guessing at what production looks like. You are implementing a documented industry pattern and then testing it harder than the docs do.

---

## Part 3 — Failure scenarios (written before the architecture)

Design components from failures, not the other way around. Each of these is a test **and** a pre-written interview story.

| \# | Scenario | Pass criterion |
| :---- | :---- | :---- |
| 1 | **Duplicate delivery** — the same event is ingested three times | Billed once; dedup enforced by a database constraint, not application logic |
| 2 | **Crash mid-aggregation** — job dies at customer 4,000 of 10,000 | Rerun produces byte-identical invoices; no partial state, no double-billing |
| 3 | **Late arrival** — an event timestamped Jan 31 arrives Feb 2, after close | Lands in January if the period is open; otherwise becomes an explicit February adjustment line. Never silently dropped |
| 4 | **Mid-period price change** — the rate changes on the 14th | Usage before the change bills at the old rate; the boundary is exact to the second |
| 5 | **Tier boundary** — a customer crosses from tier 2 into tier 3 mid-period | Marginal pricing correct at the exact crossing unit, not the whole-bucket approximation |
| 6 | **Reconciliation** — raw events versus issued invoice | Independent recomputation matches to the cent; a deliberately corrupted invoice is caught |
| 7 | **Float drift** — 10M events at $0.0001 each | Zero cent-level error across the full period |
| 8 | **Backfill safety** — re-run ingestion for the last 7 days | Totals do not change. If they do, dedup or backfill is broken |

Scenario 7 looks trivial and is not. It's also the fastest possible signal that you have thought about money before, because the answer is "integer cents, never `double`," and a surprising number of candidates get it wrong.

Scenario 8 is lifted directly from Stripe's own recommended smoke test. Citing where a test came from is a good look.

### Architecture, derived from those scenarios

Every component traces to a scenario. Nothing is here because it's "standard architecture."

- **Ingestion API** — accepts usage events, requires a deterministic event id per event, validates and rejects malformed input rather than absorbing it → 1, 8  
- **Raw event store** — append-only, immutable, time-partitioned. Corrections are new adjustment rows, never edits. This is the source of truth you can always recompute from → 3, 6  
- **Dedup layer** — uniqueness enforced by a `UNIQUE` constraint in Postgres. The database is the only thing that can't lose a race → 1  
- **Aggregation engine** — rolls raw events into per-customer, per-meter, per-period counters. Idempotent, so a rerun is a no-op → 2, 8  
- **Pricing engine** — a pure function: `(usage, plan, period) → invoice lines`. No I/O, no clock, no randomness. Tiered and volume pricing, time-sliced rates → 4, 5, 7  
- **Invoice generator** — materializes lines and totals in integer cents → 7  
- **Reconciliation job** — independently re-sums the raw events and compares against issued invoices. Fails loudly on any mismatch → 6  
- **Drill-down query** — given an invoice line, return the exact events behind it → 6  
- **Kafka** — enters in Week 4 for volume and replay, not Week 1

### Scope guard

One event schema. Two pricing models (tiered and volume). One currency. Two rate versions comparable at a time. No taxes. No dunning. No payment provider integration. No multi-tenancy beyond `customer_id`. No UI beyond Grafana and a CLI.

**If you integrate Stripe before reconciliation works, you have added an API client where the project needed a proof.**

---

## Part 4 — Technology choices, as interview defenses

Every choice is written "X over Y because Z," where Z is a correctness property or a market signal — never familiarity or trendiness. Expect to be grilled on anything you put on a resume.

- **Java 21 over Python** — Java dominates backend JDs, and money code wants a strong type system. `BigDecimal` for rate math, records for immutable domain objects, and sealed interfaces for the pricing model hierarchy. The pricing engine being genuinely pure is easier to *enforce* in a language with real immutability.  
    
- **Postgres as system of record over "just use Mongo"** — the invariant depends on database-level guarantees. A `UNIQUE` constraint on the event id is what makes dedup correct under concurrency; an application-level check-then-insert has a race window, and a good interviewer will find it in about nine seconds. You also need transactions and the ability to join events to invoice lines for drill-down.  
    
- **Integer cents over floating point, everywhere** — `0.1 + 0.2 != 0.3` in binary floating point, and at 10 million events that error is visible on the invoice. Store units as `BIGINT`, prices as scaled integers, and use `BigDecimal` only where division forces it, with an explicit rounding mode you can name out loud.  
    
- **Kafka for ingestion over a direct API write** — justified here rather than decorative. Usage events are a genuine high-volume stream, they arrive out of order, and you need replay. Replay is the load-bearing part: when you find a pricing bug in March, you recompute February from the raw log. That's the argument for a log, and it's honest.  
    
- **At-least-once delivery with an idempotent writer over exactly-once config** — "duplicates are harmless because the write is idempotent, so at-least-once is sufficient" is a design argument. "I turned on the EOS flag" is a config change. Only one of those survives a follow-up question.  
    
- **Testcontainers over mocks for integration tests** — your invariant is enforced by a database constraint, so a test with a mocked repository proves nothing about the thing that actually matters. Real Postgres, real Kafka, in the test suite.  
    
- **jqwik (property-based testing) over example-based tests for the pricing engine** — pricing has infinite inputs and a few laws that must always hold: totals are monotonic in usage, tier boundaries are continuous, and splitting a period and summing equals pricing it whole. Assert the laws, let the framework hunt for counterexamples. This is the single most senior-signaling choice on the list.  
    
- **Flyway for migrations** — billing schemas change, and "how would you ship a schema change to this?" is a question you want a one-word answer to.  
    
- **kind over EKS** — free, reproducible, skill transfers verbatim, survives the "why not EKS?" follow-up.

**Deliberately absent:** no microservices (one service, clear module boundaries — splitting this into five services would be resume theater and would make the invariant *harder* to hold). No Redis (Postgres is fast enough at this scale; adding a cache before you have a measured latency problem is decoration). No React dashboard.

---

## Part 5 — The 5-week plan

**Sequencing principle:** the component whose bug silently corrupts money ships first. Infrastructure polish ships last. Week 1's exit criterion is a *correctness assertion*, never "it's deployed."

Every week ends with a passing failure-scenario test or a measured benchmark. Never "worked on X."

### Week 1 — Correctness spine

Build: event schema, ingestion API, Postgres store with the uniqueness constraint, and a synthetic event generator that produces usage with a **known correct total**.

- The generator is not a detail. You must know the right answer in advance or you cannot tell correct from plausible  
- Deterministic event ids derived from the business action, not auto-increment  
- Raw store is append-only from day one — build the habit before it's inconvenient

**Exit:** ingest the same batch three times; the stored count and computed total are correct. Re-run ingestion for a 7-day window; totals do not move. *(Scenarios 1, 8\)*

### Week 2 — Pricing engine

Build: the pure pricing function. Tiered and volume pricing, time-sliced rate versions.

- Pure: same input, same output, no `Instant.now()` anywhere in the module  
- Property-based tests for the laws: monotonicity, tier continuity, period-split additivity  
- Integer cents end to end; one explicit rounding decision, documented

**Exit:** tier boundaries and mid-period rate changes are exact, and a 10M-event run at $0.0001 shows zero drift. *(Scenarios 4, 5, 7\)*

### Week 3 — Aggregation and invoicing

Build: the period-close run, made idempotent and crash-safe. Explicit late-arrival policy.

- Aggregation writes are idempotent — rerunning the job is a no-op, not a doubling  
- Late events land in an open period, or generate a visible adjustment line in the next one  
- Kill the job at a random point, restart, compare

**Exit:** the job dies mid-run, gets rerun, and produces byte-identical invoices. A late event is either absorbed or adjusted, and you can point at which. *(Scenarios 2, 3\)*

### Week 4 — Reconciliation and scale

Build: the self-check job, plus Kafka in front for real volume. Deploy on `kind`.

- Reconciliation independently re-sums raw events per customer per period and diffs against issued invoices  
- Deliberately corrupt one invoice row and confirm it gets caught, named, and quantified  
- Load 10M events; measure sustained ingest throughput and close-run duration  
- `kubectl delete pod` mid-ingest

**Exit:** a real events/sec number, and a planted corruption detected automatically. A killed pod loses and duplicates nothing. *(Scenario 6\)*

### Week 5 — Drill-down and ship

Build: invoice-line-to-events query, Grafana, README, write-up.

- README's hero is the **reconciliation report and a drill-down**, not the architecture diagram  
- Write-up leads with the invisibility of the problem, not the throughput number  
- Fill every `[X]` in the bullets below with a number your tests actually produced

**Exit:** someone can take an invoice total and walk it all the way back to the raw events, and you can talk through all eight failure scenarios without notes.

---

## Part 6 — The resume bullets this must earn

Draft these **now**, before writing code. Any feature that doesn't move a placeholder gets cut or marked stretch.

1. "Built a usage metering and billing engine ingesting **\[X\]M** events at **\[Y\]**/s, producing invoices reconciled to the cent against an immutable raw event log." → the headline; leads with scale but lands on correctness.  
     
2. "Guaranteed exactly-once billing via database-enforced idempotency and crash-safe idempotent aggregation, verified across **\[N\]** injected failure scenarios including mid-run crashes, duplicate delivery, and late-arriving events." → this bullet is *why* fault injection exists in Weeks 1, 3, and 4\. It is not optional.  
     
3. "Implemented tiered and time-sliced pricing in integer cents with property-based tests, eliminating float drift across **\[X\]M** events at $0.0001/unit." → the bullet that says you've thought about money.  
     
4. "Built an automated reconciliation job that independently recomputes invoices from raw events, detecting **\[N\]**/**\[N\]** injected billing corruptions." → the differentiator. Everything above this line is a pipeline; this line is a proof.

**Every claimed metric must be producible by a specific test in the roadmap above.** If you can't point at the test, delete the bullet.

---

## Part 7 — How to talk about it

### The 30-second version

"Companies that bill by usage are counting billions of events and nobody checks the arithmetic. I built a metering and billing engine where the defining feature is that it independently recomputes every invoice from the raw event log and fails loudly if they disagree. Then you can click any line on any invoice and see the exact events behind it."

### Questions you will get, and where the answer lives

| Question | Where you already answered it |
| :---- | :---- |
| "How do you handle duplicate events?" | Deterministic ids, `UNIQUE` constraint, Scenario 1 |
| "What if the billing job crashes halfway?" | Idempotent aggregation, Scenario 2 |
| "Why not exactly-once semantics in Kafka?" | Part 4 — idempotent writer makes it unnecessary |
| "How do you store money?" | Integer cents, Scenario 7, with the 0.1+0.2 answer ready |
| "What happens if an event shows up late?" | Explicit policy, Scenario 3 — and the honest answer that both branches are visible, never silent |
| "How would you know if this was wrong in production?" | The reconciliation job. This is the question the project was built to answer |

### Outreach

**Sequence: ship first, then message.** An artifact-first message is a different conversation than a cold ask.

- Lead with the reconciliation report — the screenshot where the system catches its own planted error  
- Ask fifteen minutes on how their team handles metering-to-billing drift  
- Never open with a job ask

Template shape: *"I built \[X\] to understand \[Y\] — would you have 15 minutes to tell me how your team actually handles it?"*


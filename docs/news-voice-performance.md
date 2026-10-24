# BBS02 voice intent and catalog search performance

## Request flow

Audio capture and transcription are unchanged. The voice-intent endpoint first validates current selection/history and handles deterministic commands and known catalog names locally. An unknown multilingual name or a complex sentence uses `OpenAiNewsVoiceIntentClient`: one Responses request returns intent slots, up to four multilingual game queries, and an explicit ambiguity flag. Gemini is no longer a Spring component in this voice-intent path. The shared slot contract is provider independent. News briefing generation's Gemini integration is unchanged.

Expanded names are searched against actual database rows. A unique exact canonical translated title may resolve directly if the interpretation is unambiguous. Partial/fuzzy matches, conflicting abbreviations, duplicate titles, more-than-five results, and unspecified demo/playtest/DLC variants retain confirmation safeguards. Contextual candidate judgment may make an additional GPT call; interpretation and expansion are never separately called in the main voice-intent path. The model never supplies a trusted appId. Support policy and stored briefing availability are read fresh.

## Search and caches

- `game_search_names.compact_name` is populated with the same Java normalization used for queries, and indexed by `idx_game_search_compact`.
- Exact canonical/alias/compact matching is combined into one indexed, grouped query. Grouping happens before the six-result limit, preserving collisions even when one game has several equivalent aliases.
- Existing null keys are populated in batches of 500 in one CASE update per batch before catalog synchronization. New and modified rows receive keys through entity lifecycle callbacks. The existing five-minute catalog refresh remains.
- Name search caches immutable results for 30 seconds (misses: five seconds), at most 512 keys. Entity changes invalidate this JVM's cache after commit; JDBC backfill also changes its revision. Results read in an outer write transaction are not cached. Other application instances observe changes by TTL expiry.
- Model interpretation is cached in memory for five minutes, at most 256 entries. The exact cleaned utterance and the full validated context/history are part of the key. Failures are not cached. No candidate tokens, owner-specific selection state, support policy or final response is cached.
- Existing AI budgets/timeouts continue to apply to actual provider calls. Optional interpretation failures fall back to local candidates; complex requests with no local interpretation report the provider error.

## Schema and rollout

The current project's `ddl-auto: update` adds the nullable `compact_name` column and index. For deployments using manual migrations, add `compact_name varchar(255)` and its index before deploying these queries, then enable catalog synchronization to backfill. Wait for `select count(*) from game_search_names where compact_name is null` to reach zero before serving voice search on a newly migrated database. A production migration should stage the backfill before switching traffic; startup readiness routing is not implemented by this change.

The development database was verified read-only after backfill: 133,365 rows, zero null keys. EXPLAIN selected `idx_game_search_compact` with access type `ref` for the compact-key probe. Do not delete this additive column during a code rollback; the old code ignores it.

## Verification, 2026-10-24

55 related tests across 15 suites passed, including one HTTP request producing both intent and variants, malformed output/number/provenance rejection, context-sensitive cache isolation and expiry, cache reuse, bulk key backfill, arbitrary catalog names, ambiguous abbreviations, and owner/version-bound candidate confirmation.

The pre-change source was restored from backups and run on an isolated loopback server with collection, catalog synchronization, seed import, and schema updates disabled. The current BBS02 server ran the changed code. Both queried the same database through direct IPv4 loopback requests, alternating old/new calls for each utterance. There were three requests per utterance per version, all 24 returned the expected appId. The after-server was restarted first, clearing its caches.

| Input | Before: median of 3 (ms) | After: first request (ms) | After: median of next 2 (ms) |
|---|---:|---:|---:|
| 도타 투 패치 | 981 | 224 | 9 |
| 카운터스트라이크2 패치 | 970 | 16 | 19.5 |
| 도타 투 업데이트 내용 좀 정리해 줘 | 2126 | 1254 | 15 |
| 카운터스트라이크2 업데이트 내용 좀 정리해 줘 | 2076 | 1817 | 8 |

The samples include HTTP request/response and game/intent resolution, not microphone capture, its three-second silence window, STT, news-list retrieval or TTS. They are a small local development sample, not latency guarantees. Cold requests for different game names still incur provider time and may require candidate judgment. The first request for one game can also benefit from JVM/database warmup performed by another game; first request does not imply a fresh JVM per row.

An initial Vite-proxy comparison using `localhost` contained approximately two seconds of client connection delay. It is retained separately as diagnostic data and is not the source of the table above. Raw direct results are in the task's `voice-performance-update/direct-comparison.json`.

Nine additional real-provider/database checks passed: Korean/English/abbreviated names for Dota 2, Counter-Strike 2, Half-Life 2, Team Fortress 2, Terraria and Garry's Mod; TF2 and GTA returned choices and accepted subsequent owner-bound numbered selection. Some games correctly retain an UNSUPPORTED action according to existing news policy while resolving the expected appId.

TTS and STT code were not modified. The comparison server is stopped after verification; the current BBS02 development server remains running.

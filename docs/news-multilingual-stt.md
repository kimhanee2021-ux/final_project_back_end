# BBS02 catalog-wide multilingual voice search

## Flow

`audio -> OpenAI transcription -> intent slots -> active catalog names/aliases -> bounded name expansion -> DB candidates -> contextual candidate decision or clarification`

The resolver applies to every row in the local catalog. There are no game-ID branches for Dota or Counter-Strike.

- Exact canonical/localized aliases and normalized names resolve directly when unique.
- Catalog synchronization derives abbreviations from all eligible canonical names and imported aliases. It preserves numeric/Roman sequel tokens and derives core-title forms for edition suffixes.
- Imported Korean/English names remain attached to their existing appId. Renaming a game updates generated abbreviations without deleting imported aliases.
- Near spellings are bounded suggestions that always require confirmation; different sequel numbers cannot fuzzy-match.
- Unmatched localized names, and ambiguous short abbreviations, can use OpenAI to propose at most four alternative search terms. Proposed names are searched against the database; generated IDs are never accepted.
- A known translated full title outranks abbreviation variants from the same expansion, preventing unrelated abbreviation matches from polluting clear requests.
- The model receives the recognized request, verified current game/recent history (up to four turns), and at most five candidate names/match types. It returns a one-based candidate index or ambiguity. Responses use strict JSON Schema and `store=false`.
- Invalid/out-of-range selections, uncertainty, truncated candidate sets, provider errors, typo-only matches, and unspecified playtest/demo candidates fall back to clarification. Broad abbreviations spanning multiple games without a selected-game context cannot auto-select.
- Candidate selection keeps the existing owner/version-bound, expiring, single-use tokens. The UI supports candidate buttons or spoken ordinal numbers.

## Catalog data and limitations

The live catalog contained 69,919 games and 63,432 generated acronym rows during verification. The small `game-voice-names.json` file is now supplemental seed data imported into the shared alias table for backward compatibility, not the runtime search scope. STT hints come only from the selected database game and its active aliases; there is no fixed two-game global vocabulary.

This does not pretranslate all 69,919 names into Korean. Existing localized aliases are used first; missing cross-language names use bounded model expansion. Unknown names, absent database games, ambiguous editions, and unsupported pronunciations can still require a more specific name or Steam ID. Model-suggested names are not automatically saved as trusted aliases.

Game identity resolution is independent of news support and stored briefing availability. This change does not collect news, create summaries, or declare every game news-supported.

## Configuration and data transmission

- Transcription: existing `OPENAI_API_KEY`; `OPENAI_STT_MODEL` defaults to `gpt-transcribe`.
- Catalog reasoning: same existing key; `OPENAI_CATALOG_MODEL` defaults to `gpt-4.1-mini`.
- Overrides: `app.news.catalog.api-key`, `app.news.catalog.model`, `app.news.catalog.base-url`.
- Catalog calls share the existing bounded voice AI quota. The frontend intent timeout is 45 seconds to cover intent extraction and name reasoning.
- The user explicitly approved OpenAI transmission of recognized requests, recent context, and candidate information. The UI discloses the voice/context transmission.
- No new Gemini egress was introduced; existing intent extraction is unchanged. TTS/narration is unchanged.
- Audio capture remains bounded to 30 seconds/4 MB, with silence/manual completion and cancellation. Application code does not persist original audio.

## Verification

- 58 backend tests, including arbitrary new catalog names, imported Korean aliases, generated abbreviations, rename cleanup, collision/series handling, Roman numerals, provider failure, invalid selections, playtest confirmation, and translated-title priority.
- 22 frontend capture/narration/regression tests; production build and changed-module ESLint passed.
- Nine live requests exercised actual OpenAI reasoning and the real catalog: Dota, Counter-Strike, Half-Life, Team Fortress, Terraria, Garry's Mod, and GTA. TF2/GTA ambiguities were confirmed through owner-bound ordinal selection.
- Some correctly identified games returned `UNSUPPORTED` because news support is not registered; identity was verified separately through the returned appId.
- An existing development server temporarily returned 404 during incremental rebuilds. Restarting the BBS02 backend restored its routes, and all nine final live cases passed.
- Actual user microphone/accent coverage is not established by text reasoning tests. Prior STT validation used synthetic Korean/English audio.

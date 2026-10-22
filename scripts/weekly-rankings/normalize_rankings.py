"""Pure offline validation of the archived Steam revenue ranking dataset."""
import datetime as dt
import json
import re
from urllib.parse import urlsplit

VERIFIED = {'VERIFIED', 'VERIFIED_WITH_METADATA_GAPS'}
STATUSES = VERIFIED | {'COUNTRY_NOT_PUBLISHED', 'WEEK_NOT_PUBLISHED'}
COUNTRIES = set('US CN GB FR JP KR AU AE NO IT ES DE IL ID BR CL EG ZA IN'.split())


def require(condition, message):
    if not condition:
        raise ValueError(message)


def date(value):
    parsed = dt.date.fromisoformat(value)
    require(parsed.isoformat() == value and parsed.weekday() == 1, f'Invalid Tuesday: {value}')
    return parsed


def timestamp(value):
    parsed = dt.datetime.fromisoformat(value)
    require(parsed.tzinfo is not None, 'Timestamp requires timezone')
    return parsed.astimezone(dt.timezone.utc).replace(tzinfo=None)


def url(value, nullable=False):
    if value is None and nullable:
        return
    parsed = urlsplit(value)
    require(parsed.scheme == 'https' and parsed.hostname and not parsed.username and not parsed.password,
            'Expected HTTPS URL without credentials')


def integer(value, minimum, label):
    require(type(value) is int and value >= minimum, f'Invalid {label}')


def normalize(data):
    require(data['metric'] == 'revenue', 'Only revenue rankings supported')
    countries, snapshots, products, entries = {}, {}, {}, {}
    for original in data['products']:
        p = dict(original)
        key = p['steam_item_key']
        require(key not in products, f'Duplicate product: {key}')
        match = re.fullmatch(r'(app|package|bundle)_([1-9][0-9]*)', key)
        require(match is not None, f'Invalid product key: {key}')
        for kind in ('app', 'package', 'bundle'):
            expected = int(match[2]) if match[1] == kind else None
            require(p[f'steam_{kind}_id'] == expected, f'Product ID mismatch: {key}')
        p['title'] = p['title'].strip()
        require(0 < len(p['title']) <= 512, f'Invalid title: {key}')
        url(p['store_url'], nullable=True)
        url(p['image_url'], nullable=True)
        url(p['metadata_source_url'])
        timestamp(p['metadata_checked_at_utc'])
        for field in ('developers', 'publishers'):
            require(isinstance(p[field], list) and all(isinstance(s, str) for s in p[field]), field)
            p[field] = list(dict.fromkeys(s.strip() for s in p[field] if s.strip()))
        products[key] = p
    for original in data['coverage']:
        s = dict(original)
        c, w = s['country_code'], s['week_start']
        require(c in COUNTRIES, f'Unexpected country: {c}')
        require(c not in countries or countries[c] == s['country_name_ko'], 'Conflicting country name')
        countries[c] = s['country_name_ko']
        require((c, w) not in snapshots, f'Duplicate snapshot: {c}/{w}')
        require(date(s['week_end_exclusive']) - date(w) == dt.timedelta(days=7), 'Invalid period')
        require(s['status'] in STATUSES, f'Invalid status: {s["status"]}')
        require(s['scope'] in {'LATEST_AVAILABLE_FIVE', 'LATEST_COMPLETED_WEEK_PROBE'}, 'Invalid scope')
        require(bool(re.fullmatch('[0-9a-f]{64}', s['source_sha256'])), 'Invalid source hash')
        timestamp(s['checked_at_utc'])
        url(s['source_url'])
        if s['status'] in VERIFIED:
            require(s['actual_country_code'] == c and s['actual_week_start'] == w,
                    f'Redirected source cannot be ranked as {c}/{w}')
        snapshots[c, w] = s
        entries[c, w] = []
    seen = set()
    for original in data['weekly_rankings']:
        r = dict(original)
        key = r['country_code'], r['week_start']
        require(key in snapshots and snapshots[key]['status'] in VERIFIED, 'Entry without verified snapshot')
        require(r['steam_item_key'] in products, 'Unknown product reference')
        integer(r['source_rank'], 1, 'rank')
        require(r['source_rank'] <= 10, 'Rank exceeds TOP10')
        require((*key, r['steam_item_key']) not in seen, 'Duplicate product within chart')
        seen.add((*key, r['steam_item_key']))
        integer(r['consecutive_weeks'], 1, 'consecutive weeks')
        require(type(r['first_top100']) is bool, 'Invalid first_top100')
        require(r['metric'] == 'revenue', 'Invalid row metric')
        require(r['source_url'] == snapshots[key]['source_url'], 'Entry source mismatch')
        require(timestamp(r['checked_at_utc']) == timestamp(snapshots[key]['checked_at_utc']), 'Entry timestamp mismatch')
        require(isinstance(r['source_title'], str) and 0 < len(r['source_title']) <= 512, 'Invalid source title')
        kind, change = r['rank_change_kind'], r['rank_change']
        require(kind in {'UP', 'DOWN', 'SAME', 'NEW', 'RETURNING', 'UNKNOWN'}, 'Invalid change kind')
        if kind in {'UP', 'DOWN', 'SAME'}:
            integer(r['previous_rank_raw'], 1, 'previous rank')
            require(type(change) is int and change == r['previous_rank_raw'] - r['source_rank'], 'Invalid rank delta')
            require((kind == 'UP' and change > 0) or (kind == 'DOWN' and change < 0)
                    or (kind == 'SAME' and change == 0), 'Delta direction mismatch')
        else:
            require(change is None, 'New/returning/unknown delta must be null')
        # Prices are observation-time data, never historical ranking prices.
        r.pop('observed_price_text', None)
        r.pop('pricing_country_context', None)
        entries[key].append(r)
    for key, s in snapshots.items():
        expected = list(range(1, 11)) if s['status'] in VERIFIED else []
        rows = sorted(entries[key], key=lambda r: r['source_rank'])
        require([r['source_rank'] for r in rows] == expected, f'Missing/duplicate rank: {key}')
        require(s['rank_count'] == len(rows), f'Coverage count mismatch: {key}')
        entries[key] = rows
    count = sum(map(len, entries.values()))
    require(data['ranking_count'] == count, 'Manifest ranking count mismatch')
    require(data['countries_requested'] == len(countries), 'Manifest country count mismatch')
    require(data['countries_collected'] == len({c for (c, w), rows in entries.items() if rows}), 'Collected country count mismatch')
    return dict(countries=[{'country_code': c, 'name_ko': n} for c, n in sorted(countries.items())],
                products=list(products.values()), snapshots=list(snapshots.values()),
                entries=[r for rows in entries.values() for r in rows])


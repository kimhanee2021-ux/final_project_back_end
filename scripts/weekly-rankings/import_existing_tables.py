"""Offline by default. --apply imports into rank_weekly_*; --migrate applies the checked migration."""
import argparse
import datetime as dt
import json
from pathlib import Path
from existing_db import connect, ROOT
from normalize_rankings import normalize, timestamp

STATUS = {'VERIFIED':'VERIFIED','VERIFIED_WITH_METADATA_GAPS':'VERIFIED_GAPS',
          'COUNTRY_NOT_PUBLISHED':'COUNTRY_UNAVAILABLE','WEEK_NOT_PUBLISHED':'WEEK_UNAVAILABLE'}
TABLES = ('rank_weekly_charts','rank_weekly_entries')


def prepare(path):
    data = normalize(json.loads(path.read_text(encoding='utf-8-sig')))
    products = {p['steam_item_key']:p for p in data['products']}
    charts, entries = [], []
    for s in data['snapshots']:
        charts.append(dict(country_code=s['country_code'],week_start=s['week_start'],
                           week_end_exclusive=s['week_end_exclusive'],source_type='STEAM_WEEKLY',
                           verification_status=STATUS[s['status']],source_url=s['source_url'],
                           fetched_at=timestamp(s['checked_at_utc'])))
    for r in data['entries']:
        p = products[r['steam_item_key']]
        if not p['steam_app_id']:
            raise ValueError('Non-app product cannot fit existing schema')
        if len(p['title']) > 500:
            raise ValueError('Product title exceeds 500 characters')
        entries.append(dict(country_code=r['country_code'],week_start=r['week_start'],
                            app_id=p['steam_app_id'],game_name_snapshot=p['title'],
                            steam_rank=r['source_rank'],game_rank=r['source_rank'],
                            last_week_rank=r['previous_rank_raw'],consecutive_weeks=r['consecutive_weeks'],
                            image_url_snapshot=p['image_url'],rank_change_kind=r['rank_change_kind']))
    if any(len(c['source_url']) > 500 for c in charts):
        raise ValueError('Source URL exceeds 500 characters')
    return charts, entries


def backup(cursor):
    directory = ROOT/'db-backups'/dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    directory.mkdir(parents=True)
    for table in TABLES:
        cursor.execute(f'SHOW CREATE TABLE {table}')
        (directory/f'{table}.sql').write_text(cursor.fetchone()[1]+';\n',encoding='utf8')
        cursor.execute(f'SELECT * FROM {table}')
        columns = [c[0] for c in cursor.description]
        (directory/f'{table}.json').write_text(json.dumps([dict(zip(columns,row)) for row in cursor.fetchall()],
                                                        ensure_ascii=False,indent=2,default=str),encoding='utf8')
    return str(directory)


def apply(charts, entries, migrate):
    conn = connect()
    cursor = conn.cursor()
    locked = False
    try:
        cursor.execute("SELECT GET_LOCK('gamesense_rank_weekly_import',10)")
        if cursor.fetchone()[0] != 1:
            raise ValueError('Another import is running')
        locked = True
        saved = backup(cursor)
        conn.rollback()
        print('Backup:',saved)
        if migrate:
            cursor.execute('SHOW CREATE TABLE rank_weekly_entries')
            ddl = cursor.fetchone()[1]
            if 'image_url_snapshot' in ddl or 'rank_change_kind' in ddl:
                raise ValueError('Migration already/partially applied; inspect before retrying')
            if '`chk_game_rank` CHECK ((`game_rank` between 1 and 5))' not in ddl:
                raise ValueError('Unexpected CHECK constraint; migration refused')
            cursor.execute((ROOT/'migrate_existing_top10.sql').read_text(encoding='utf8'))
            print('Migration applied (MySQL DDL commits separately).')
        cursor.execute('SHOW CREATE TABLE rank_weekly_entries')
        ddl = cursor.fetchone()[1]
        if not all(x in ddl for x in ['image_url_snapshot','rank_change_kind','between 1 and 10']):
            raise ValueError('TOP10 migration required')
        conn.rollback()
        conn.begin()
        ids = {}
        skipped = set()
        # Existing matching charts are verified, not overwritten.
        grouped = {}
        for e in entries:
            grouped.setdefault((e['country_code'],e['week_start']),[]).append(e)
        for c in charts:
            key = c['country_code'],c['week_start']
            cursor.execute('SELECT chart_id,week_end_exclusive,source_type,verification_status,source_url,fetched_at FROM rank_weekly_charts WHERE country_code=%s AND week_start=%s FOR UPDATE',key)
            old = cursor.fetchone()
            if old:
                expected = (dt.date.fromisoformat(c['week_end_exclusive']),c['source_type'],c['verification_status'],c['source_url'],c['fetched_at'])
                if old[1:] != expected:
                    raise ValueError(f'Existing chart differs: {key}; no overwrite performed')
                cursor.execute('SELECT app_id,game_name_snapshot,steam_rank,game_rank,last_week_rank,consecutive_weeks,image_url_snapshot,rank_change_kind FROM rank_weekly_entries WHERE chart_id=%s ORDER BY steam_rank',(old[0],))
                expected_entries = [tuple(e[k] for k in ['app_id','game_name_snapshot','steam_rank','game_rank','last_week_rank','consecutive_weeks','image_url_snapshot','rank_change_kind']) for e in sorted(grouped.get(key,[]),key=lambda e:e['steam_rank'])]
                if list(cursor.fetchall()) != expected_entries:
                    raise ValueError(f'Existing entries differ: {key}; no overwrite performed')
                ids[key]=old[0]
                skipped.add(key)
            else:
                columns=list(c)
                cursor.execute(f'INSERT INTO rank_weekly_charts ({",".join(columns)}) VALUES ({",".join(["%s"]*len(columns))})',list(c.values()))
                ids[key]=cursor.lastrowid
        for e in entries:
            key=e['country_code'],e['week_start']
            if key in skipped:
                continue
            row={k:v for k,v in e.items() if k not in ('country_code','week_start')}
            row['chart_id']=ids[key]
            cursor.execute(f'INSERT INTO rank_weekly_entries ({",".join(row)}) VALUES ({",".join(["%s"]*len(row))})',list(row.values()))
        for c in charts:
            key=c['country_code'],c['week_start']
            cursor.execute('SELECT COUNT(*),MIN(steam_rank),MAX(steam_rank),COUNT(DISTINCT steam_rank) FROM rank_weekly_entries WHERE chart_id=%s',(ids[key],))
            expected=(10,1,10,10) if c['verification_status'] in ('VERIFIED','VERIFIED_GAPS') else (0,None,None,0)
            if cursor.fetchone()!=expected:
                raise ValueError(f'Post-import validation failed: {key}')
        conn.commit()
        result=dict(charts=len(charts),entries=len(entries),identical_charts_skipped=len(skipped),
                    committed_at_utc=dt.datetime.now(dt.timezone.utc).isoformat(),backup=saved)
        (ROOT/'existing_import_result.json').write_text(json.dumps(result,indent=2),encoding='utf8')
        print(json.dumps(result))
    except Exception:
        conn.rollback()
        raise
    finally:
        if locked:
            cursor.execute("SELECT RELEASE_LOCK('gamesense_rank_weekly_import')")
            cursor.fetchone()
        cursor.close()
        conn.close()


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input',type=Path,default=ROOT/'data/dataset.json')
    parser.add_argument('--apply',action='store_true')
    parser.add_argument('--migrate',action='store_true')
    args=parser.parse_args()
    if args.migrate and not args.apply:
        parser.error('--migrate requires --apply')
    charts,entries=prepare(args.input)
    print(json.dumps(dict(mode='APPLY' if args.apply else 'OFFLINE',charts=len(charts),entries=len(entries))))
    if args.apply:
        apply(charts,entries,args.migrate)

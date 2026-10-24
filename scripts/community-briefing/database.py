"""Export/import only briefing tables. Secrets remain in the specified local .env."""
import argparse, os, re, subprocess, urllib.parse
from pathlib import Path
p=argparse.ArgumentParser()
p.add_argument('action', choices=['export','import'])
p.add_argument('--env-file',required=True)
p.add_argument('--file',required=True)
p.add_argument('--mysql-bin',default='')
a=p.parse_args()
c={}
for line in Path(a.env_file).read_text(encoding='utf-8-sig').splitlines():
    m=re.match(r'^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$',line)
    if m:c[m[1]]=m[2].strip(chr(34)+chr(39))
u=urllib.parse.urlparse(c['DB_URL'].removeprefix('jdbc:'))
db=u.path.strip('/')
if not re.fullmatch(r'[A-Za-z0-9_]+',db):raise SystemExit('Invalid database name')
e=os.environ.copy();e['MYSQL_PWD']=c.get('DB_PASSWORD','')
exe=Path(a.mysql_bin)/('mysqldump' if a.action=='export' else 'mysql')
cmd=[str(exe),'--host='+str(u.hostname),'--port='+str(u.port or 3306),'--user='+c['DB_USERNAME'],'--default-character-set=utf8mb4']
if a.action=='export':
    cmd+=['--single-transaction','--skip-add-drop-table','--no-tablespaces','--set-gtid-purged=OFF',db,'ai_official_news','ai_game_briefings','ai_briefing_checkpoint']
    with open(a.file,'wb') as f:result=subprocess.run(cmd,stdout=f,stderr=subprocess.PIPE,env=e)
else:
    cmd+=[db]
    with open(a.file,'rb') as f:result=subprocess.run(cmd,stdin=f,stdout=subprocess.DEVNULL,stderr=subprocess.PIPE,env=e)
if result.returncode:raise SystemExit('Database operation failed; credentials are not logged')
print('Briefing '+a.action+' completed. No user/member tables included.')

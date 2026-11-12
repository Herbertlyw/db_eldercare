"""Real full plus binary-log recovery into a separate disposable database."""
from db import sql,CONFIG,ROOT
import subprocess,os,json,hashlib,datetime
out=ROOT/'.local/backups';out.mkdir(exist_ok=True)
env={**os.environ,'MYSQL_PWD':CONFIG['root']}
args=[CONFIG['mysqldump'],'--no-defaults','--socket='+CONFIG['socket'],'-uroot','--single-transaction','--routines','--triggers','--events','--set-gtid-purged=OFF','--no-tablespaces','eldercare']
r=subprocess.run(args,env=env,capture_output=True)
if r.returncode:raise RuntimeError(r.stderr.decode())
full=out/'full.sql';full.write_bytes(r.stdout)
status=sql('SHOW BINARY LOG STATUS;').stdout.splitlines()[1].split('\t');logname,pos=status[0],int(status[1])
sql("INSERT INTO service_item(name,category,unit,price,duration_minutes,subsidizable) VALUES('恢复演练标记','演练','VISIT',1,1,0);")
end=sql('SHOW BINARY LOG STATUS;').stdout.splitlines()[1].split('\t');stop=int(end[1])
sql('DROP DATABASE IF EXISTS eldercare_restore; CREATE DATABASE eldercare_restore CHARACTER SET utf8mb4;')
sql(full.read_text(),'eldercare_restore')
base_count=sql('SELECT COUNT(*) FROM service_item;','eldercare_restore').stdout.strip().splitlines()[-1]
r=subprocess.run([CONFIG['mysqlbinlog'],'--no-defaults','--start-position='+str(pos),'--stop-position='+str(stop),'--rewrite-db=eldercare->eldercare_restore',str(ROOT/'.local/native-mysql'/logname)],capture_output=True)
if r.returncode:raise RuntimeError(r.stderr.decode())
(out/'incremental.sql').write_bytes(r.stdout)
sql(r.stdout.decode(),'eldercare_restore')
recovered=sql("SELECT COUNT(*) FROM service_item WHERE name='恢复演练标记';",'eldercare_restore').stdout.strip().splitlines()[-1]
assert recovered=='1'
tables=sql("SELECT table_name FROM information_schema.tables WHERE table_schema='eldercare' AND table_type='BASE TABLE';").stdout.splitlines()[1:]
counts=[]
for t in tables:
 a=sql(f'SELECT COUNT(*) FROM `{t}`;').stdout.splitlines()[-1];b=sql(f'SELECT COUNT(*) FROM `{t}`;','eldercare_restore').stdout.splitlines()[-1];assert a==b,(t,a,b);counts.append({'table':t,'source':int(a),'restored':int(b)})
report={'time':datetime.datetime.now().isoformat(),'backup_sha256':hashlib.sha256(full.read_bytes()).hexdigest(),'binlog':logname,'start_position':pos,'end_position':stop,'base_service_items':int(base_count),'incremental_marker_recovered':True,'counts':counts,'scope':'full SQL plus one actual binlog insert; restored in separate database'}
(ROOT/'evidence/recovery.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
# Remove only this drill's synthetic catalog item, keep evidence and recovery database.
sql("DELETE FROM service_item WHERE name='恢复演练标记';")
print('Full + incremental restore verified:',len(counts),'table counts matched')

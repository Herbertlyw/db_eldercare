"""Task-local native MySQL, leaves other instances untouched."""
import json,subprocess,os
from pathlib import Path
root=Path(__file__).resolve().parents[1]
p=root/'.local/config.json';c=json.loads(p.read_text())
base=Path('/Users/herbert/.codex/visualizations/2026/11/02/01a19bfe-5d5c-7833-84e8-9f51b7091fa3/experiment6/mysql-8.4.11-macos15-arm64')
data=root/'.local/native-mysql';data.mkdir(exist_ok=True)
socket='/tmp/db-course-eldercare.sock'
if not (data/'mysql').exists():
 r=subprocess.run([str(base/'bin/mysqld'),'--no-defaults','--initialize-insecure','--basedir='+str(base),'--datadir='+str(data)],capture_output=True,text=True)
 if r.returncode:raise RuntimeError(r.stderr)
subprocess.run(['docker','stop','db-course-eldercare'],capture_output=True)
r=subprocess.run([str(base/'bin/mysqld'),'--no-defaults','--daemonize','--basedir='+str(base),'--datadir='+str(data),'--socket='+socket,'--pid-file='+str(root/'.local/mysql.pid'),'--log-error='+str(root/'.local/mysql.log'),'--port=33316','--bind-address=127.0.0.1','--mysqlx=0','--log-bin=mysql-bin','--server-id=216','--default-time-zone=+08:00'],capture_output=True,text=True)
if r.returncode:raise RuntimeError(r.stderr)
cmd=[str(base/'bin/mysql'),'--no-defaults','--socket='+socket,'-uroot']
statement=f"ALTER USER 'root'@'localhost' IDENTIFIED BY '{c['root']}'; CREATE DATABASE eldercare CHARACTER SET utf8mb4; CREATE USER 'elder_app'@'localhost' IDENTIFIED BY '{c['app']}'; GRANT SELECT, INSERT, UPDATE, DELETE, EXECUTE ON eldercare.* TO 'elder_app'@'localhost';"
r=subprocess.run(cmd,input=statement,text=True,capture_output=True)
if r.returncode:raise RuntimeError(r.stderr)
c.update(mysql_bin=str(base/'bin/mysql'),mysqldump=str(base/'bin/mysqldump'),mysqlbinlog=str(base/'bin/mysqlbinlog'),socket=socket)
p.write_text(json.dumps(c));os.chmod(p,0o600)
print('Native MySQL ready on 127.0.0.1:33316')

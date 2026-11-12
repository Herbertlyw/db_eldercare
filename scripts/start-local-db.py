"""Restart this workspace's existing native MySQL; never initialize or overwrite data."""
from pathlib import Path
import json, subprocess, os

root = Path(__file__).resolve().parents[1]
config = json.loads((root / '.local/config.json').read_text())
base = Path(config['mysql_bin']).parent.parent
data = root / '.local/native-mysql'
env = {**os.environ, 'MYSQL_PWD': config['root']}
probe = subprocess.run([config['mysql_bin'], '--no-defaults', '--socket=' + config['socket'], '-uroot', '-e', 'SELECT 1'], env=env, capture_output=True)
if probe.returncode == 0:
    print('课程数据库已经运行。')
elif not (data / 'mysql').is_dir():
    raise SystemExit('未找到已有课程数据库，已停止。请勿用此脚本初始化数据库。')
else:
    result = subprocess.run([str(base / 'bin/mysqld'), '--no-defaults', '--daemonize', '--basedir=' + str(base), '--datadir=' + str(data), '--socket=' + config['socket'], '--pid-file=' + str(root / '.local/mysql.pid'), '--log-error=' + str(root / '.local/mysql.log'), '--port=33316', '--bind-address=127.0.0.1', '--mysqlx=0', '--log-bin=mysql-bin', '--server-id=216', '--default-time-zone=+08:00'], capture_output=True, text=True)
    if result.returncode:
        raise SystemExit(result.stderr)
    print('课程数据库已经启动：127.0.0.1:33316。')

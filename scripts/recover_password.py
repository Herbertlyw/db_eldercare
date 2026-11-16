"""Local maintenance: recover an active staff account without exposing a password in arguments.
Requires this developer machine's private config. Other machines can use the documented Java command.
"""
import argparse
import getpass
import os
import re
import subprocess
from db import CONFIG, ROOT

parser = argparse.ArgumentParser(description='恢复账号密码，用户下次登录必须修改')
parser.add_argument('username', help='要恢复的登录账号')
parser.add_argument('--reason', required=True, help='身份核实和重置原因，不填写任何密码')
parser.add_argument('--database', default='eldercare')
args = parser.parse_args()
if not re.fullmatch(r'eldercare(?:_[a-z0-9_]+)?', args.database):
    parser.error('数据库名称无效')
password = getpass.getpass('临时密码（至少10个字符）: ')
if password != getpass.getpass('再次输入临时密码: '):
    raise SystemExit('两次密码不一致，未修改账号')
env = {**os.environ, 'DB_URL':f'jdbc:mysql://127.0.0.1:33316/{args.database}?serverTimezone=Asia/Shanghai',
       'DB_USER':'elder_app', 'DB_PASSWORD':CONFIG['app'], 'FIELD_KEY':CONFIG['key'],
       'RECOVERY_USERNAME':args.username, 'RECOVERY_PASSWORD':password, 'RECOVERY_REASON':args.reason}
result = subprocess.run(['java','-jar',str(ROOT/'target/eldercare-1.0.0.jar'),
                         '--app.password-recovery=true','--app.jobs=false',
                         '--spring.main.web-application-type=none'],cwd=ROOT,env=env)
raise SystemExit(result.returncode)

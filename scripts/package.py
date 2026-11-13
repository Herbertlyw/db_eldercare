"""Build a reviewable delivery archive; exclude workstation secrets and database files."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import shutil, hashlib, json

root = Path(__file__).resolve().parents[1]
stage = root / '.local/delivery/左邻右舍养老课程设计'
if stage.exists():
    shutil.rmtree(stage)
stage.mkdir(parents=True)
for name in ['database', 'src', 'docs', 'scripts', 'evidence']:
    shutil.copytree(root / name, stage / name, ignore=shutil.ignore_patterns('__pycache__', '*.pyc', '.DS_Store', '.~*', '~$*'))
for name in ['README.md', 'pom.xml', '初始化Windows.bat', '启动Windows.bat']:
    shutil.copy2(root / name, stage / name)
shutil.copy2(root / 'target/eldercare-1.0.0.jar', stage / 'eldercare.jar')
(stage / 'outputs').mkdir()
for p in (root / 'outputs').iterdir():
    if p.is_file() and not p.name.startswith(('.', '~$')) and p.suffix in ['.docx', '.pdf', '.pptx']:
        shutil.copy2(p, stage / 'outputs' / p.name)
(stage / '先读我.txt').write_text(
    '左邻右舍社区居家养老智慧服务平台\n\n'
    'Windows：安装 Java 17 与 MySQL 8，加入 PATH。首次运行 初始化Windows.bat，之后运行 启动Windows.bat。\n'
    '页面：http://127.0.0.1:18080\n'
    '不需要 Docker、Python 或 Maven。Windows 脚本尚未经过实机验证。\n'
    '首次初始化会生成本机密码及密钥，账号说明保存在 .local/演示账号.txt。\n'
    '不要复制他人的数据库密码，也不要丢失本机字段加密密钥。\n\n'
    '源码、SQL、设计图、测试证据及 Word/PDF 报告均已包含。\n'
    'docs 中提供代码导读和答辩讲稿。outputs 中的 PPTX 可在 WPS 或 PowerPoint 中打开。\n'
    '小组真实成员、实际贡献、协作证据和全员发言视频仍需本人补齐。个人报告是预填稿。\n'
    'scripts 中的 Python/Node 辅助程序用于本机开发和材料生成，不是 Windows 启动前提。\n', encoding='utf-8-sig')
config = json.loads((root / '.local/config.json').read_text())
secrets = [str(config[k]).encode() for k in ['root', 'app', 'demo', 'key']]
manifest = {}
for p in sorted(stage.rglob('*')):
    if not p.is_file():
        continue
    body = p.read_bytes()
    assert not any(secret in body for secret in secrets), 'Secret found in ' + str(p.relative_to(stage))
    if p.suffix in ['.jar', '.docx', '.pptx']:
        with ZipFile(p) as nested:
            assert nested.testzip() is None
            for member in nested.namelist():
                assert not any(secret in nested.read(member) for secret in secrets), 'Secret found in embedded package'
    manifest[str(p.relative_to(stage))] = hashlib.sha256(body).hexdigest()
(stage / 'SHA256SUMS.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding='utf8')
target = root / 'outputs/左邻右舍养老平台_运行与课程材料.zip'
with ZipFile(target, 'w', ZIP_DEFLATED) as z:
    for p in sorted(stage.rglob('*')):
        if p.is_file():
            z.write(p, p.relative_to(stage.parent))
with ZipFile(target) as z:
    assert z.testzip() is None
    assert all('/.local/' not in n and '/.git/' not in n for n in z.namelist())
    for name, digest in manifest.items():
        assert hashlib.sha256(z.read(stage.name + '/' + name)).hexdigest() == digest
print('Validated archive:', target.name, 'files:', len(manifest), 'bytes:', target.stat().st_size)

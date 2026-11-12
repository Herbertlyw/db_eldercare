$ErrorActionPreference = 'Stop'
[Console]::InputEncoding = [System.Text.Encoding]::UTF8
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
$Root = Split-Path $PSScriptRoot -Parent
Set-Location $Root
if (!(Get-Command java -ErrorAction SilentlyContinue)) { throw '请安装 Java 17 并加入 PATH。' }
if (!(Get-Command mysql -ErrorAction SilentlyContinue)) { throw '请安装 MySQL 8.0.16 以上版本，并将 bin 目录加入 PATH。' }
$HostName = Read-Host '数据库主机（直接回车使用 127.0.0.1）'
if (!$HostName) { $HostName = '127.0.0.1' }
$Port = Read-Host '数据库端口（直接回车使用 3306）'
if (!$Port) { $Port = '3306' }
$Admin = Read-Host '数据库管理员账号（直接回车使用 root）'
if (!$Admin) { $Admin = 'root' }
$Secure = Read-Host '数据库管理员密码' -AsSecureString
$Ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Secure)
$PreviousPassword = $env:MYSQL_PWD
try {
 $env:MYSQL_PWD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($Ptr)
 $MysqlArgs = @('--default-character-set=utf8mb4', '-h', $HostName, '-P', $Port, '-u', $Admin)
 $Existing = 'SHOW DATABASES LIKE ''eldercare'';' | & mysql @MysqlArgs --batch --skip-column-names
 if ($LASTEXITCODE -ne 0) { throw '数据库连接失败。' }
 if ($Existing) { throw 'eldercare 已存在。为保护已有数据，初始化已停止。请使用已有配置启动，或由管理员明确选择新库名。' }
 function RandomHex([int]$Bytes) { $Buffer = New-Object byte[] $Bytes; $Rng = [Security.Cryptography.RandomNumberGenerator]::Create(); $Rng.GetBytes($Buffer); $Rng.Dispose(); return -join ($Buffer | ForEach-Object { $_.ToString('x2') }) }
 $AppPassword = RandomHex 24
 $DemoPassword = RandomHex 10
 $Key = RandomHex 32
 $Init = "CREATE DATABASE eldercare CHARACTER SET utf8mb4; CREATE USER 'elder_course'@'localhost' IDENTIFIED BY '$AppPassword'; GRANT SELECT, INSERT, UPDATE, DELETE, EXECUTE ON eldercare.* TO 'elder_course'@'localhost';"
 $Init | & mysql @MysqlArgs
 if ($LASTEXITCODE -ne 0) { throw '建库或创建专用账号失败，请查看上方错误。未删除任何已有数据库。' }
 foreach ($File in @('01_schema.sql','02_routines.sql','03_views.sql')) {
  Get-Content (Join-Path $Root "database/$File") -Raw -Encoding UTF8 | & mysql @MysqlArgs eldercare
  if ($LASTEXITCODE -ne 0) { throw "导入 $File 失败，请查看错误。" }
 }
 New-Item -ItemType Directory -Force '.local' | Out-Null
 $Config = @{ DB_URL="jdbc:mysql://${HostName}:${Port}/eldercare?serverTimezone=Asia/Shanghai"; DB_USER='elder_course'; DB_PASSWORD=$AppPassword; FIELD_KEY=$Key; DEMO_PASSWORD=$DemoPassword }
 $Config | ConvertTo-Json | Set-Content '.local/windows-config.json' -Encoding UTF8
 foreach ($K in $Config.Keys) { [Environment]::SetEnvironmentVariable($K,$Config[$K],'Process') }
 $Jar = Join-Path $Root 'eldercare.jar'
 if (!(Test-Path $Jar)) { $Jar = Join-Path $Root 'target/eldercare-1.0.0.jar' }
 & java -jar $Jar --app.seed=true --app.jobs=false --spring.main.web-application-type=none
 if ($LASTEXITCODE -ne 0) { throw '演示数据导入失败。' }
 "账号：admin1 / family1 / caregiver1 / manager1 / street1 / regulator1`r`n共同演示密码：$DemoPassword`r`n全部为虚构课程数据，请勿公开此文件。" | Set-Content '.local/演示账号.txt' -Encoding UTF8
 Write-Host '初始化完成。运行 scripts/start-windows.ps1，浏览器打开 http://127.0.0.1:18080。'
 Write-Host '演示账号见 .local/演示账号.txt。请保管配置文件和字段加密密钥。'
} finally {
 $env:MYSQL_PWD = $PreviousPassword
 [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($Ptr)
}

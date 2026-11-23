# 人员账号、角色权限、密码重置与账号启停测试。
param([string]$BaseUrl = 'http://localhost:8081')

$ErrorActionPreference = 'Stop'
$base = $BaseUrl.TrimEnd('/')
$pass = 0
$fail = 0

function Check([string]$name, [bool]$ok, [string]$detail = '') {
    if ($ok) { $script:pass++; Write-Host "PASS  $name" -ForegroundColor Green }
    else { $script:fail++; Write-Host "FAIL  $name  $detail" -ForegroundColor Red }
}

function Login([string]$user, [string]$password) {
    $response = Invoke-WebRequest "$base/api/auth/login" -Method Post `
        -ContentType 'application/json' `
        -Body (@{ username = $user; password = $password } | ConvertTo-Json) `
        -SessionVariable web -SkipHttpErrorCheck
    return [pscustomobject]@{ Web = $web; Response = $response }
}

function UserBody($username, $displayName, $password, $role) {
    return @{
        username = $username
        displayName = $displayName
        password = $password
        role = $role
    } | ConvertTo-Json
}

$adminLogin = Login 'admin' '123456'
$admin = $adminLogin.Web
$inspector = (Login 'liyi' '123456').Web
$suffix = Get-Date -Format 'MMddHHmmss'
$username = "autotest$suffix"
$initialPassword = 'Init1234'
$newPassword = 'Reset5678'

$r = Invoke-WebRequest "$base/api/users" -Method Post -WebSession $inspector `
    -ContentType 'application/json' -Body (UserBody 'denied-user' '无权限人员' '12345678' 'INSPECTOR') `
    -SkipHttpErrorCheck
Check '检验人员不能新增账号' ($r.StatusCode -eq 403) "status=$($r.StatusCode)"

$r = Invoke-WebRequest "$base/api/users" -Method Post -WebSession $admin `
    -ContentType 'application/json' -Body (UserBody 'ab' '账号过短' '12345678' 'INSPECTOR') `
    -SkipHttpErrorCheck
Check '非法登录账号被拒绝' ($r.StatusCode -eq 400) "status=$($r.StatusCode)"

$r = Invoke-WebRequest "$base/api/users" -Method Post -WebSession $admin `
    -ContentType 'application/json' -Body (UserBody "weak$suffix" '弱密码' '123' 'INSPECTOR') `
    -SkipHttpErrorCheck
Check '弱密码被拒绝' ($r.StatusCode -eq 400) "status=$($r.StatusCode)"

$r = Invoke-WebRequest "$base/api/users" -Method Post -WebSession $admin `
    -ContentType 'application/json' `
    -Body (UserBody "longpass$suffix" '超长密码' ('中' * 30) 'INSPECTOR') `
    -SkipHttpErrorCheck
Check '超过 BCrypt 字节上限的密码被拒绝' ($r.StatusCode -eq 400) "status=$($r.StatusCode)"

$r = Invoke-WebRequest "$base/api/users" -Method Post -WebSession $admin `
    -ContentType 'application/json' -Body (UserBody "role$suffix" '非法角色' '12345678' 'ROOT') `
    -SkipHttpErrorCheck
Check '非法角色被拒绝' ($r.StatusCode -eq 400) "status=$($r.StatusCode)"

$r = Invoke-WebRequest "$base/api/users" -Method Post -WebSession $admin `
    -ContentType 'application/json' `
    -Body (UserBody $username '自动测试人员' $initialPassword 'INSPECTOR') -SkipHttpErrorCheck
$created = ($r.Content | ConvertFrom-Json).data
Check '管理员新增自定义账号' ($r.StatusCode -eq 200 -and $created.displayName -eq '自动测试人员' -and $created.role -eq 'INSPECTOR') `
    "status=$($r.StatusCode)"
Check '新账号默认启用' ($created.status -eq 'ACTIVE' -and -not $created.cardNo)

$r = Invoke-WebRequest "$base/api/users" -Method Post -WebSession $admin `
    -ContentType 'application/json' `
    -Body (UserBody $username '重复账号' '12345678' 'INSPECTOR') -SkipHttpErrorCheck
Check '重复登录账号被拒绝' ($r.StatusCode -eq 400) "status=$($r.StatusCode)"

$newLogin = Login $username $initialPassword
Check '新账号可用自定义密码登录' ($newLogin.Response.StatusCode -eq 200 -and
    (($newLogin.Response.Content | ConvertFrom-Json).data.role -eq 'INSPECTOR'))
$newSession = $newLogin.Web

$r = Invoke-WebRequest "$base/api/users" -Method Post -WebSession $newSession `
    -ContentType 'application/json' -Body (UserBody "selfmade$suffix" '自建账号' '12345678' 'ADMIN') `
    -SkipHttpErrorCheck
Check '检验人员不能自行提升权限' ($r.StatusCode -eq 403) "status=$($r.StatusCode)"

$r = Invoke-WebRequest "$base/api/users/$($created.id)" -Method Put -WebSession $admin `
    -ContentType 'application/json' `
    -Body (@{ displayName = '自动测试复核员'; role = 'REVIEWER'; status = 'ACTIVE' } | ConvertTo-Json) `
    -SkipHttpErrorCheck
$updated = ($r.Content | ConvertFrom-Json).data
Check '管理员修改姓名并分配复核角色' ($r.StatusCode -eq 200 -and
    $updated.displayName -eq '自动测试复核员' -and $updated.role -eq 'REVIEWER')

$r = Invoke-WebRequest "$base/api/auth/me" -WebSession $newSession -SkipHttpErrorCheck
$me = ($r.Content | ConvertFrom-Json).data
Check '角色变更对现有会话立即生效' ($r.StatusCode -eq 200 -and
    $me.displayName -eq '自动测试复核员' -and $me.role -eq 'REVIEWER')

$r = Invoke-WebRequest "$base/api/users/$($created.id)/password" -Method Put -WebSession $inspector `
    -ContentType 'application/json' -Body (@{ password = $newPassword } | ConvertTo-Json) `
    -SkipHttpErrorCheck
Check '非管理员不能重置密码' ($r.StatusCode -eq 403) "status=$($r.StatusCode)"

$r = Invoke-WebRequest "$base/api/users/$($created.id)/password" -Method Put -WebSession $admin `
    -ContentType 'application/json' -Body (@{ password = $newPassword } | ConvertTo-Json) `
    -SkipHttpErrorCheck
Check '管理员重置密码' ($r.StatusCode -eq 200) "status=$($r.StatusCode)"

$r = Invoke-WebRequest "$base/api/auth/me" -WebSession $newSession -SkipHttpErrorCheck
Check '重置密码后旧会话立即失效' ($r.StatusCode -eq 401) "status=$($r.StatusCode)"

$oldLogin = Login $username $initialPassword
Check '旧密码重置后失效' ($oldLogin.Response.StatusCode -eq 401) `
    "status=$($oldLogin.Response.StatusCode)"
$newLogin = Login $username $newPassword
Check '新密码可以登录' ($newLogin.Response.StatusCode -eq 200) `
    "status=$($newLogin.Response.StatusCode)"
$newSession = $newLogin.Web

$r = Invoke-WebRequest "$base/api/users/$($created.id)" -Method Put -WebSession $admin `
    -ContentType 'application/json' `
    -Body (@{ displayName = '自动测试复核员'; role = 'REVIEWER'; status = 'INACTIVE' } | ConvertTo-Json) `
    -SkipHttpErrorCheck
Check '管理员停用账号' ($r.StatusCode -eq 200 -and
    (($r.Content | ConvertFrom-Json).data.status -eq 'INACTIVE'))

$r = Invoke-WebRequest "$base/api/auth/me" -WebSession $newSession -SkipHttpErrorCheck
Check '停用后现有会话立即失效' ($r.StatusCode -eq 401) "status=$($r.StatusCode)"
$disabledLogin = Login $username $newPassword
Check '停用账号不能登录' ($disabledLogin.Response.StatusCode -eq 401) `
    "status=$($disabledLogin.Response.StatusCode)"

$r = Invoke-WebRequest "$base/api/users/$($created.id)" -Method Put -WebSession $admin `
    -ContentType 'application/json' `
    -Body (@{ displayName = '自动测试复核员'; role = 'REVIEWER'; status = 'ACTIVE' } | ConvertTo-Json) `
    -SkipHttpErrorCheck
Check '管理员重新启用账号' ($r.StatusCode -eq 200)

$adminUser = @(((Invoke-WebRequest "$base/api/users" -WebSession $admin).Content |
    ConvertFrom-Json).data) | Where-Object username -eq 'admin' | Select-Object -First 1
$r = Invoke-WebRequest "$base/api/users/$($adminUser.id)" -Method Put -WebSession $admin `
    -ContentType 'application/json' `
    -Body (@{ displayName = $adminUser.displayName; role = 'ADMIN'; status = 'INACTIVE' } | ConvertTo-Json) `
    -SkipHttpErrorCheck
Check '管理员不能停用自己' ($r.StatusCode -eq 400) "status=$($r.StatusCode)"

$r = Invoke-WebRequest "$base/api/logs?action=USER_CREATE&size=100" -WebSession $admin -SkipHttpErrorCheck
$logs = @((($r.Content | ConvertFrom-Json).data.items))
Check '新增人员写入审计日志' (@($logs | Where-Object {
    $_.action -eq 'USER_CREATE' -and $_.detail -like "$username*"
}).Count -ge 1)

$r = Invoke-WebRequest "$base/api/users" -Method Post -WebSession $admin `
    -ContentType 'application/json' -Body '{invalid-json' -SkipHttpErrorCheck
$badJson = $r.Content | ConvertFrom-Json
Check '格式错误请求不泄露内部异常' ($r.StatusCode -eq 400 -and
    $badJson.message -eq '请求数据格式不正确')

$r = Invoke-WebRequest "$base/api/users/not-a-number" -Method Put -WebSession $admin `
    -ContentType 'application/json' `
    -Body (@{ displayName = '测试'; role = 'INSPECTOR'; status = 'ACTIVE' } | ConvertTo-Json) `
    -SkipHttpErrorCheck
Check '非法路径参数返回安全的 400' ($r.StatusCode -eq 400 -and
    (($r.Content | ConvertFrom-Json).message -eq '请求参数格式不正确'))

$r = Invoke-WebRequest "$base/api/users/$($created.id)" -Method Delete `
    -WebSession $inspector -SkipHttpErrorCheck
Check '非管理员不能删除人员' ($r.StatusCode -eq 403) "status=$($r.StatusCode)"

$r = Invoke-WebRequest "$base/api/users/$($created.id)" -Method Delete `
    -WebSession $admin -SkipHttpErrorCheck
Check '管理员删除人员账号' ($r.StatusCode -eq 200) "status=$($r.StatusCode)"

$r = Invoke-WebRequest "$base/api/users" -WebSession $admin -SkipHttpErrorCheck
$remainingUsers = @((($r.Content | ConvertFrom-Json).data))
Check '删除后人员列表不再显示' (@($remainingUsers | Where-Object {
    $_.id -eq $created.id
}).Count -eq 0)

$deletedLogin = Login $username $newPassword
Check '删除后账号不能登录' ($deletedLogin.Response.StatusCode -eq 401) `
    "status=$($deletedLogin.Response.StatusCode)"

$r = Invoke-WebRequest "$base/api/users/$($created.id)" -Method Delete `
    -WebSession $admin -SkipHttpErrorCheck
Check '重复删除返回 404' ($r.StatusCode -eq 404) "status=$($r.StatusCode)"

$r = Invoke-WebRequest "$base/api/logs?action=USER_DELETE&size=100" -WebSession $admin -SkipHttpErrorCheck
$deleteLogs = @((($r.Content | ConvertFrom-Json).data.items))
Check '删除动作写入审计日志' (@($deleteLogs | Where-Object {
    $_.detail -like "$username*"
}).Count -ge 1)

$r = Invoke-WebRequest "$base/api/users/$($adminUser.id)" -Method Delete `
    -WebSession $admin -SkipHttpErrorCheck
Check '管理员不能删除自己' ($r.StatusCode -eq 400) "status=$($r.StatusCode)"

Write-Host ""
Write-Host ("结果：{0} 通过，{1} 失败" -f $pass, $fail) `
    -ForegroundColor $(if ($fail -eq 0) { 'Green' } else { 'Red' })
exit $(if ($fail -eq 0) { 0 } else { 1 })

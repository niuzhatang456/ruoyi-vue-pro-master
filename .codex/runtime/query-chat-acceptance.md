# Jijian Query Chat Acceptance

This note is for local verification only. Do not paste or store a real API key here.

## 1. Verify DeepSeek environment

```powershell
$key = [Environment]::GetEnvironmentVariable('DEEPSEEK_API_KEY','Machine')
[PSCustomObject]@{
  apiKeyConfigured = [bool]$key
  apiKeyLength = if ($key) { $key.Length } else { 0 }
  baseUrl = if ($env:DEEPSEEK_BASE_URL) { $env:DEEPSEEK_BASE_URL } else { 'https://api.deepseek.com' }
  model = if ($env:DEEPSEEK_MODEL) { $env:DEEPSEEK_MODEL } else { 'deepseek-v4-pro' }
}
```

## 2. Login and capture token

```powershell
$loginBody = @{ username='admin'; password='admin123'; captchaVerification='' } | ConvertTo-Json
$login = Invoke-RestMethod `
  -Uri 'http://127.0.0.1:48080/admin-api/system/auth/login' `
  -Method Post `
  -ContentType 'application/json; charset=utf-8' `
  -Headers @{ 'tenant-id'='1' } `
  -Body $loginBody
$token = $login.data.accessToken
[PSCustomObject]@{ code=$login.code; hasToken=[bool]$token; userId=$login.data.userId }
```

## 3. Shared chat headers

```powershell
$headers = @{
  'Content-Type' = 'application/json; charset=utf-8'
  'Authorization' = 'Bearer ' + $token
  'tenant-id' = '1'
}
```

## 4. CANTEEN_SUPPLY normal question

```powershell
$body = @{
  formType = 'CANTEEN_SUPPLY'
  message = '分析不同采价点的同项目价格差异'
  history = @()
} | ConvertTo-Json -Depth 10
$resp = Invoke-RestMethod -Uri 'http://127.0.0.1:48080/admin-api/jijian/query/chat' -Method Post -Headers $headers -Body ([Text.Encoding]::UTF8.GetBytes($body))
$resp.data | Select-Object aiMode, formType, answer
$resp.data.summary | Select-Object totalCount,totalProjectCount,priceVarianceCount
```

## 5. CANTEEN_SUPPLY boundary question

```powershell
$body = @{
  formType = 'CANTEEN_SUPPLY'
  message = '哪个部门食堂浪费最严重'
  history = @()
} | ConvertTo-Json -Depth 10
$resp = Invoke-RestMethod -Uri 'http://127.0.0.1:48080/admin-api/jijian/query/chat' -Method Post -Headers $headers -Body ([Text.Encoding]::UTF8.GetBytes($body))
$resp.data | Select-Object aiMode, answer
```

Expected: the answer says the canteen supplier table only contains item name, spec/level, unit, price, and price point.

## 6. REAL_ESTATE question

```powershell
$body = @{
  formType = 'REAL_ESTATE'
  message = '分析房屋出租信息的租赁情况、合同金额和即将到期合同'
  history = @()
} | ConvertTo-Json -Depth 10
$resp = Invoke-RestMethod -Uri 'http://127.0.0.1:48080/admin-api/jijian/query/chat' -Method Post -Headers $headers -Body ([Text.Encoding]::UTF8.GetBytes($body))
$resp.data | Select-Object aiMode, formType, answer
$resp.data.summary | Select-Object propertyTotalCount,tenantTotalCount,contractTotalCount,totalContractAmount,expiredContractCount,expiringSoonContractCount
```

Expected: no raw phone number, ID card, or business license values appear.

## 7. ATTENDANCE question

```powershell
$body = @{
  formType = 'ATTENDANCE'
  message = '分析本月考勤异常情况'
  history = @()
} | ConvertTo-Json -Depth 10
$resp = Invoke-RestMethod -Uri 'http://127.0.0.1:48080/admin-api/jijian/query/chat' -Method Post -Headers $headers -Body ([Text.Encoding]::UTF8.GetBytes($body))
$resp.data | Select-Object aiMode, formType, answer
$resp.data.summary | Select-Object totalCount,departmentCount
```

## 8. Confirm DeepSeek mode and key safety

`aiMode=DEEPSEEK_SUMMARY` means DeepSeek produced the final Chinese summary. `DEEPSEEK_INTENT` means DeepSeek parsed intent but local text was used. `LOCAL_FALLBACK` means DeepSeek did not successfully participate.

Search logs for accidental key exposure:

```powershell
Select-String -Path '.codex/runtime/*.log' -Pattern 'Authorization|Bearer|sk-' -CaseSensitive:$false
```

Expected: no real API key is printed.

$BASE_URL = "http://localhost:8080"

function Register-User($loginId) {
    $body = @{
        loginId  = $loginId
        password = "Test1234!"
        name     = "Tester"
        birthday = "19900101"
        email    = "$loginId@test.com"
    } | ConvertTo-Json

    try {
        $res = Invoke-WebRequest -Uri "$BASE_URL/api/v1/members" -Method POST `
            -ContentType "application/json" -Body $body -UseBasicParsing
        Write-Host "OK: $loginId"
    } catch {
        Write-Host "SKIP: $loginId ($($_.Exception.Response.StatusCode.value__))"
    }
}

1..100 | ForEach-Object { Register-User "user$_" }
Register-User "tokenuser"

Write-Host "완료"

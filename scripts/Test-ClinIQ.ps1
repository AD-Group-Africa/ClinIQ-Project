#Requires -Version 5.1
param([string]$BaseUrl = "http://localhost:8080")
$ErrorActionPreference = "SilentlyContinue"

$Timestamp  = Get-Date -Format "yyyyMMddHHmmss"
$ClinicSlug = "test-$Timestamp"
$AdminEmail = "admin.$Timestamp@test.com"
$AdminPass  = "ClinIQ@Test2024"
$Global:Token    = ""
$Global:ClinicId = ""
$Global:PatientId = ""
$Passed = 0; $Failed = 0

function Pass([string]$m) { $script:Passed++; Write-Host "  [OK]  $m" -ForegroundColor Green }
function Fail([string]$m) { $script:Failed++; Write-Host "  [FAIL] $m" -ForegroundColor Red }
function Skip([string]$m) { Write-Host "  [SKIP] $m" -ForegroundColor Yellow }
function Step([string]$m) { Write-Host ""; Write-Host "  >>> $m" -ForegroundColor Cyan }

function Call {
    param([string]$Method="GET",[string]$Path,[object]$Body,[switch]$NoAuth)
    $h = @{ "Content-Type"="application/json" }
    if (-not $NoAuth -and $Global:Token)    { $h["Authorization"] = "Bearer $($Global:Token)" }
    if ($Global:ClinicId)                   { $h["X-Clinic-ID"]   = $Global:ClinicId }
    $p = @{ Uri="$BaseUrl$Path"; Method=$Method; Headers=$h; UseBasicParsing=$true }
    if ($Body) { $p["Body"] = ($Body | ConvertTo-Json -Depth 10) }
    try { return Invoke-RestMethod @p } catch { return $null }
}

# ── connectivity ─────────────────────────────────────────────
Write-Host ""
Write-Host "  ========================================"
Write-Host "  ClinIQ API Test Suite"
Write-Host "  Target: $BaseUrl"
Write-Host "  ========================================"

$ping = Call -Path "/actuator/health" -NoAuth
if (-not $ping) {
    Write-Host "  [FAIL] Cannot reach $BaseUrl - is Docker running?" -ForegroundColor Red
    Write-Host "  Run: .\scripts\Launch-ClinIQ.ps1"
    exit 1
}
Write-Host "  [OK]  Backend reachable" -ForegroundColor Green

# ── AUTH ─────────────────────────────────────────────────────
Step "Auth"
$reg = Call POST "/api/v1/auth/register" -NoAuth @{
    clinicName=$("Test Clinic $Timestamp"); clinicSlug=$ClinicSlug
    firstName="Admin"; lastName="User"
    email=$AdminEmail; password=$AdminPass; phone="+254700000001"
}
if ($reg -and $reg.success) {
    $Global:Token    = $reg.data.accessToken
    $Global:ClinicId = $reg.data.user.clinicId
    Pass "Clinic registered + token obtained"
} else { Fail "Registration failed"; exit 1 }

$login = Call POST "/api/v1/auth/login" -NoAuth @{ email=$AdminEmail; password=$AdminPass }
if ($login -and $login.success) { $Global:Token = $login.data.accessToken; Pass "Login OK" }
else { Fail "Login failed" }

$me = Call "/api/v1/auth/me"
if ($me -and $me.success) { Pass "GET /auth/me -> $($me.data.email)" } else { Fail "GET /auth/me" }

# ── PATIENTS ─────────────────────────────────────────────────
Step "Patients"
$pt = Call POST "/api/v1/patients" @{
    firstName="Amina"; lastName="Hassan"; phone="+254712345678"
    email="amina.$Timestamp@test.com"; gender="FEMALE"; dateOfBirth="1990-03-15"
}
if ($pt -and $pt.success) { $Global:PatientId = $pt.data.id; Pass "Patient created: $($pt.data.patientNumber)" }
else { Fail "Patient create failed" }

$search = Call "/api/v1/patients?q=Amina"
if ($search -and $search.success) { Pass "Patient search OK ($($search.data.Count) results)" }
else { Fail "Patient search" }

if ($Global:PatientId) {
    $get = Call "/api/v1/patients/$($Global:PatientId)"
    if ($get -and $get.success) { Pass "GET patient by ID" } else { Fail "GET patient by ID" }
}

# ── APPOINTMENTS ─────────────────────────────────────────────
Step "Appointments"
$queue = Call "/api/v1/appointments/today"
if ($queue -and $queue.success) { Pass "Today queue: $($queue.data.Count) appts" }
else { Fail "Today queue" }

# ── PHARMACY ─────────────────────────────────────────────────
Step "Pharmacy"
$drug = Call POST "/api/v1/pharmacy/drugs" @{
    name="Amoxicillin 500mg"; genericName="Amoxicillin"
    category="ANTIBIOTIC"; unit="CAPSULES"; reorderLevel=50; sellingPrice=15.00
}
if ($drug -and $drug.success) {
    $drugId = $drug.data.drugId
    Pass "Drug added: $($drug.data.name)"

    $stock = Call POST "/api/v1/pharmacy/stock/add" @{
        drugId=$drugId; batchNumber="AMX-001"; quantity=200
        unitCost=8.50; expiryDate=((Get-Date).AddMonths(18).ToString("yyyy-MM-dd"))
    }
    if ($stock -and $stock.success) { Pass "Stock added: 200 units" } else { Fail "Stock add" }

    $alerts = Call "/api/v1/pharmacy/alerts"
    if ($alerts -and $alerts.success) { Pass "Alerts OK" } else { Fail "Alerts" }
} else { Fail "Drug create" }

# ── LAB ──────────────────────────────────────────────────────
Step "Laboratory"
$test = Call POST "/api/v1/lab/tests" @{
    name="Full Blood Count"; code="FBC"
    category="Haematology"; price=350.00; turnaroundHours=4
}
if ($test -and $test.success) { Pass "Lab test added" } else { Fail "Lab test add" }

$pending = Call "/api/v1/lab/requests/pending"
if ($pending -and $pending.success) { Pass "Pending requests OK" } else { Fail "Pending requests" }

# ── BILLING ──────────────────────────────────────────────────
Step "Billing"
if ($Global:PatientId) {
    $inv = Call POST "/api/v1/billing/invoices" @{
        patientId=$Global:PatientId
        lineItems=@(@{ description="Consultation"; quantity=1; unitPrice=1500.00 })
        discount=0
    }
    if ($inv -and $inv.success) {
        Pass "Invoice created: $($inv.data.invoiceNumber) - KES $($inv.data.total)"
        $pay = Call POST "/api/v1/billing/payments" @{
            invoiceId=$inv.data.id; amount=1500.00; method="CASH"
        }
        if ($pay -and $pay.success) { Pass "Payment recorded" } else { Fail "Payment" }
    } else { Fail "Invoice create" }
}

$rev = Call "/api/v1/billing/reports/revenue"
if ($rev -and $rev.success) { Pass "Revenue report OK" } else { Fail "Revenue report" }

# ── ANALYTICS ────────────────────────────────────────────────
Step "Analytics"
$dash = Call "/api/v1/analytics/dashboard"
if ($dash -and $dash.success) { Pass "Dashboard: $($dash.data.todayTotal) appts today" }
else { Fail "Dashboard analytics" }

# ── SECURITY ─────────────────────────────────────────────────
Step "Security"
$noAuth = Invoke-WebRequest -Uri "$BaseUrl/api/v1/patients" -Method GET `
    -UseBasicParsing -ErrorAction SilentlyContinue
if ($noAuth -and $noAuth.StatusCode -eq 401) { Pass "Unauthenticated -> 401" }
else {
    $code = if ($noAuth) { $noAuth.StatusCode } else { "no response" }
    Fail "Expected 401, got: $code"
}

$health = Call "/actuator/health" -NoAuth
if ($health -and $health.status -eq "UP") { Pass "Health: UP" } else { Fail "Health check" }

# ── SUMMARY ──────────────────────────────────────────────────
$total = $Passed + $Failed
$pct   = if ($total -gt 0) { [math]::Round($Passed / $total * 100) } else { 0 }
Write-Host ""
Write-Host "  ========================================"
Write-Host "  RESULTS: $Passed passed, $Failed failed ($pct%)" -ForegroundColor $(if ($Failed -eq 0) { "Green" } else { "Yellow" })
Write-Host "  Clinic slug : $ClinicSlug"
Write-Host "  Admin email : $AdminEmail / $AdminPass"
if ($Global:PatientId) { Write-Host "  Patient ID  : $($Global:PatientId)" }
Write-Host "  ========================================"
Write-Host ""

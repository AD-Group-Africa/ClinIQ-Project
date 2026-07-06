# =============================================================
# Setup-ClinIQ.ps1
# Creates the complete ClinIQ monorepo from scratch.
# Run: .\Setup-ClinIQ.ps1
# Run: .\Setup-ClinIQ.ps1 -Root "D:\Projects\cliniq"
# =============================================================
param(
    [string]$Root = "C:\Users\$env:USERNAME\Desktop\CLINIQ",
    [switch]$SkipDocker
)

$ErrorActionPreference = "Stop"

function Write-Step($n, $msg) { Write-Host "`n[$n] $msg" -ForegroundColor Cyan }
function Write-Ok($msg)       { Write-Host "  ✓ $msg" -ForegroundColor Green }
function Write-Inf($msg)      { Write-Host "  · $msg" -ForegroundColor DarkGray }

Write-Host "`n  ╔══════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "  ║   ClinIQ — Project Setup              ║" -ForegroundColor Cyan
Write-Host "  ║   Target: $Root" -ForegroundColor Cyan
Write-Host "  ╚══════════════════════════════════════╝`n" -ForegroundColor Cyan

# ──────────────────────────────────────────────────────────────
# STEP 1 — Directory tree
# ──────────────────────────────────────────────────────────────
Write-Step 1 "Creating directory structure"

$dirs = @(
    # Backend
    "backend\src\main\kotlin\com\cliniq",
    "backend\src\main\kotlin\com\cliniq\config",
    "backend\src\main\kotlin\com\cliniq\shared\audit",
    "backend\src\main\kotlin\com\cliniq\shared\context",
    "backend\src\main\kotlin\com\cliniq\shared\exceptions",
    "backend\src\main\kotlin\com\cliniq\shared\response",
    "backend\src\main\kotlin\com\cliniq\infrastructure\sms",
    "backend\src\main\kotlin\com\cliniq\domain\auth\entity",
    "backend\src\main\kotlin\com\cliniq\domain\auth\repository",
    "backend\src\main\kotlin\com\cliniq\domain\auth\security",
    "backend\src\main\kotlin\com\cliniq\domain\auth\service",
    "backend\src\main\kotlin\com\cliniq\domain\auth\dto",
    "backend\src\main\kotlin\com\cliniq\domain\auth\controller",
    "backend\src\main\kotlin\com\cliniq\domain\clinic\entity",
    "backend\src\main\kotlin\com\cliniq\domain\clinic\repository",
    "backend\src\main\kotlin\com\cliniq\domain\patient\entity",
    "backend\src\main\kotlin\com\cliniq\domain\patient\repository",
    "backend\src\main\kotlin\com\cliniq\domain\patient\dto",
    "backend\src\main\kotlin\com\cliniq\domain\patient\service",
    "backend\src\main\kotlin\com\cliniq\domain\patient\controller",
    "backend\src\main\kotlin\com\cliniq\domain\appointment\entity",
    "backend\src\main\kotlin\com\cliniq\domain\appointment\repository",
    "backend\src\main\kotlin\com\cliniq\domain\appointment\dto",
    "backend\src\main\kotlin\com\cliniq\domain\appointment\service",
    "backend\src\main\kotlin\com\cliniq\domain\appointment\controller",
    "backend\src\main\kotlin\com\cliniq\domain\consultation\entity",
    "backend\src\main\kotlin\com\cliniq\domain\consultation\repository",
    "backend\src\main\kotlin\com\cliniq\domain\consultation\dto",
    "backend\src\main\kotlin\com\cliniq\domain\consultation\service",
    "backend\src\main\kotlin\com\cliniq\domain\consultation\controller",
    "backend\src\main\kotlin\com\cliniq\domain\pharmacy\entity",
    "backend\src\main\kotlin\com\cliniq\domain\pharmacy\repository",
    "backend\src\main\kotlin\com\cliniq\domain\pharmacy\dto",
    "backend\src\main\kotlin\com\cliniq\domain\pharmacy\service",
    "backend\src\main\kotlin\com\cliniq\domain\pharmacy\controller",
    "backend\src\main\kotlin\com\cliniq\domain\lab\entity",
    "backend\src\main\kotlin\com\cliniq\domain\lab\repository",
    "backend\src\main\kotlin\com\cliniq\domain\lab\dto",
    "backend\src\main\kotlin\com\cliniq\domain\lab\service",
    "backend\src\main\kotlin\com\cliniq\domain\lab\controller",
    "backend\src\main\kotlin\com\cliniq\domain\billing\entity",
    "backend\src\main\kotlin\com\cliniq\domain\billing\repository",
    "backend\src\main\kotlin\com\cliniq\domain\billing\dto",
    "backend\src\main\kotlin\com\cliniq\domain\billing\service",
    "backend\src\main\kotlin\com\cliniq\domain\billing\controller",
    "backend\src\main\kotlin\com\cliniq\domain\communication\entity",
    "backend\src\main\kotlin\com\cliniq\domain\communication\repository",
    "backend\src\main\kotlin\com\cliniq\domain\communication\service",
    "backend\src\main\kotlin\com\cliniq\domain\analytics\service",
    "backend\src\main\kotlin\com\cliniq\domain\analytics\controller",
    "backend\src\main\kotlin\com\cliniq\domain\public_api\controller",
    "backend\src\main\resources\db\migration",
    "backend\src\test\kotlin\com\cliniq",
    # Frontend
    "frontend\src\app\(auth)\login",
    "frontend\src\app\(dashboard)\reception",
    "frontend\src\app\(dashboard)\patients",
    "frontend\src\app\(dashboard)\billing",
    "frontend\src\app\(dashboard)\pharmacy",
    "frontend\src\app\(dashboard)\lab",
    "frontend\src\app\(dashboard)\analytics",
    "frontend\src\lib\api",
    "frontend\src\lib\store\slices",
    "frontend\src\types",
    "frontend\src\components\shared",
    "frontend\src\components\appointments",
    "frontend\public",
    # Chatbot
    "chatbot\src\handlers",
    "chatbot\src\engine",
    "chatbot\src\integrations",
    # DevOps
    "devops\nginx"
)

foreach ($d in $dirs) {
    New-Item -ItemType Directory -Path "$Root\$d" -Force | Out-Null
}
Write-Ok "All directories created"

# ──────────────────────────────────────────────────────────────
# STEP 2 — Gradle build files
# ──────────────────────────────────────────────────────────────
Write-Step 2 "Writing Gradle build files"

Set-Content -Path "$Root\backend\settings.gradle.kts" -Value @'
rootProject.name = "cliniq-backend"
'@

Set-Content -Path "$Root\backend\build.gradle.kts" -Encoding UTF8 -Value @'
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.6"
    kotlin("jvm") version "2.0.20"
    kotlin("plugin.spring") version "2.0.20"
    kotlin("plugin.jpa") version "2.0.20"
    kotlin("plugin.noarg") version "2.0.20"
    kotlin("plugin.allopen") version "2.0.20"
}

group   = "com.cliniq"
version = "1.0.0"
java { sourceCompatibility = JavaVersion.VERSION_21 }

repositories { mavenCentral() }

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
    implementation("org.springframework.data:spring-data-redis")
    implementation("io.lettuce:lettuce-core")
    implementation("com.africastalking:core:3.4.4")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("io.mockk:mockk:1.13.12")
    testImplementation("com.ninja-squad:springmockk:4.0.2")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
tasks.withType<Test> { useJUnitPlatform() }

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}
noArg {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
}
'@
Write-Ok "build.gradle.kts"

# ──────────────────────────────────────────────────────────────
# STEP 3 — Application config (application.yml)
# ──────────────────────────────────────────────────────────────
Write-Step 3 "Writing Spring Boot config"

Set-Content -Path "$Root\backend\src\main\resources\application.yml" -Encoding UTF8 -Value @'
spring:
  application:
    name: cliniq-backend
  datasource:
    url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/cliniq}
    username: ${DATABASE_USER:cliniq}
    password: ${DATABASE_PASSWORD:cliniq_dev_password}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
  flyway:
    enabled: true
    locations: classpath:db/migration
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:redis_dev_password}

jwt:
  secret: ${JWT_SECRET:cliniq_jwt_secret_minimum_64_chars_long_change_in_production_now_ok}
  expiration-ms: 900000
  refresh-expiration-ms: 604800000

africa-talking:
  api-key: ${AT_API_KEY:}
  username: ${AT_USERNAME:sandbox}

mpesa:
  consumer-key: ${MPESA_CONSUMER_KEY:}
  consumer-secret: ${MPESA_CONSUMER_SECRET:}
  shortcode: ${MPESA_SHORTCODE:174379}
  passkey: ${MPESA_PASSKEY:}
  callback-url: ${MPESA_CALLBACK_URL:http://localhost:8080/api/v1/billing/mpesa/callback}
  sandbox: ${MPESA_SANDBOX:true}

server:
  port: 8080

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: always
'@
Write-Ok "application.yml"

# ──────────────────────────────────────────────────────────────
# STEP 4 — Flyway SQL migrations
# ──────────────────────────────────────────────────────────────
Write-Step 4 "Writing Flyway SQL migrations"

# V1 — Core schema
Set-Content -Path "$Root\backend\src\main\resources\db\migration\V1__init_schema.sql" -Encoding UTF8 -Value @'
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- Clinics
CREATE TABLE clinics (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(200) NOT NULL,
    slug VARCHAR(100) UNIQUE NOT NULL,
    phone VARCHAR(20),
    email VARCHAR(150),
    address TEXT,
    city VARCHAR(100),
    country VARCHAR(50) DEFAULT 'KE',
    logo_url TEXT,
    website TEXT,
    timezone VARCHAR(50) DEFAULT 'Africa/Nairobi',
    currency VARCHAR(10) DEFAULT 'KES',
    subscription_tier VARCHAR(20) DEFAULT 'starter',
    subscription_expires_at TIMESTAMPTZ,
    is_active BOOLEAN DEFAULT TRUE,
    settings JSONB DEFAULT '{}',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Users
CREATE TYPE user_role AS ENUM ('SUPER_ADMIN','CLINIC_ADMIN','DOCTOR','NURSE','RECEPTIONIST','FINANCE','PATIENT');
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    clinic_id UUID REFERENCES clinics(id) ON DELETE CASCADE,
    email VARCHAR(200) UNIQUE NOT NULL,
    phone VARCHAR(20),
    password_hash TEXT NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    role user_role NOT NULL,
    avatar_url TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    is_verified BOOLEAN DEFAULT FALSE,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Doctors
CREATE TABLE doctors (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    clinic_id UUID REFERENCES clinics(id) ON DELETE CASCADE,
    specialization VARCHAR(150),
    qualification VARCHAR(200),
    license_number VARCHAR(100),
    bio TEXT,
    consultation_fee DECIMAL(10,2) DEFAULT 0,
    is_available BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Doctor schedules
CREATE TABLE doctor_schedules (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    doctor_id UUID REFERENCES doctors(id) ON DELETE CASCADE,
    clinic_id UUID REFERENCES clinics(id) ON DELETE CASCADE,
    day_of_week SMALLINT NOT NULL CHECK (day_of_week BETWEEN 0 AND 6),
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    slot_duration_minutes INTEGER DEFAULT 30,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE schedule_overrides (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    doctor_id UUID REFERENCES doctors(id) ON DELETE CASCADE,
    clinic_id UUID REFERENCES clinics(id) ON DELETE CASCADE,
    override_date DATE NOT NULL,
    is_available BOOLEAN DEFAULT FALSE,
    start_time TIME,
    end_time TIME,
    reason VARCHAR(200),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Patients
CREATE TYPE gender_type AS ENUM ('MALE','FEMALE','OTHER','PREFER_NOT_TO_SAY');
CREATE TYPE blood_group_type AS ENUM ('A+','A-','B+','B-','AB+','AB-','O+','O-','UNKNOWN');
CREATE TABLE patients (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    clinic_id UUID REFERENCES clinics(id) ON DELETE CASCADE,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    patient_number VARCHAR(30) UNIQUE,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    email VARCHAR(200),
    date_of_birth DATE,
    gender gender_type,
    national_id VARCHAR(50),
    blood_group blood_group_type DEFAULT 'UNKNOWN',
    allergies TEXT[],
    emergency_contact_name VARCHAR(200),
    emergency_contact_phone VARCHAR(20),
    insurance_provider VARCHAR(200),
    insurance_number VARCHAR(100),
    address TEXT,
    notes TEXT,
    tags TEXT[],
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Appointments
CREATE TYPE appointment_status AS ENUM ('SCHEDULED','CONFIRMED','CHECKED_IN','IN_CONSULTATION','COMPLETED','CANCELLED','NO_SHOW','RESCHEDULED');
CREATE TYPE appointment_type AS ENUM ('CONSULTATION','FOLLOW_UP','PROCEDURE','CHECK_UP','WALK_IN','TELECONSULTATION');
CREATE TABLE appointments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    clinic_id UUID REFERENCES clinics(id) ON DELETE CASCADE,
    patient_id UUID REFERENCES patients(id) ON DELETE CASCADE,
    doctor_id UUID REFERENCES doctors(id) ON DELETE SET NULL,
    appointment_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    type appointment_type DEFAULT 'CONSULTATION',
    status appointment_status DEFAULT 'SCHEDULED',
    queue_number INTEGER,
    chief_complaint TEXT,
    notes TEXT,
    booked_by UUID REFERENCES users(id),
    booked_via VARCHAR(30) DEFAULT 'PORTAL',
    reminder_sent BOOLEAN DEFAULT FALSE,
    cancellation_reason TEXT,
    rescheduled_from UUID REFERENCES appointments(id),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Communications
CREATE TYPE comm_channel AS ENUM ('SMS','WHATSAPP','EMAIL','IN_APP','PUSH');
CREATE TYPE comm_status AS ENUM ('PENDING','SENT','DELIVERED','FAILED','READ');
CREATE TYPE comm_type AS ENUM ('APPOINTMENT_REMINDER','APPOINTMENT_CONFIRMATION','APPOINTMENT_CANCELLATION','FOLLOW_UP','BILLING_REMINDER','CAMPAIGN','GENERAL');
CREATE TABLE communications (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    clinic_id UUID REFERENCES clinics(id) ON DELETE CASCADE,
    patient_id UUID REFERENCES patients(id) ON DELETE CASCADE,
    channel comm_channel NOT NULL,
    type comm_type NOT NULL,
    subject VARCHAR(300),
    body TEXT NOT NULL,
    status comm_status DEFAULT 'PENDING',
    external_ref VARCHAR(200),
    sent_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    failed_reason TEXT,
    appointment_id UUID REFERENCES appointments(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Reminders
CREATE TABLE reminders (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    clinic_id UUID REFERENCES clinics(id) ON DELETE CASCADE,
    appointment_id UUID REFERENCES appointments(id) ON DELETE CASCADE,
    patient_id UUID REFERENCES patients(id) ON DELETE CASCADE,
    send_at TIMESTAMPTZ NOT NULL,
    channel comm_channel NOT NULL,
    is_sent BOOLEAN DEFAULT FALSE,
    sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Audit logs
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    clinic_id UUID REFERENCES clinics(id) ON DELETE CASCADE,
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100),
    entity_id UUID,
    old_values JSONB,
    new_values JSONB,
    ip_address INET,
    user_agent TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Consultations
CREATE TABLE consultations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    clinic_id UUID REFERENCES clinics(id) ON DELETE CASCADE,
    appointment_id UUID UNIQUE REFERENCES appointments(id) ON DELETE SET NULL,
    patient_id UUID REFERENCES patients(id) ON DELETE CASCADE,
    doctor_id UUID REFERENCES doctors(id) ON DELETE SET NULL,
    visit_number INTEGER DEFAULT 1,
    chief_complaint TEXT,
    history TEXT,
    examination TEXT,
    diagnosis TEXT,
    diagnosis_codes TEXT[],
    treatment_plan TEXT,
    notes TEXT,
    vitals JSONB DEFAULT '{}',
    follow_up_date DATE,
    follow_up_notes TEXT,
    is_admitted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Prescriptions
CREATE TABLE prescriptions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    clinic_id UUID REFERENCES clinics(id) ON DELETE CASCADE,
    consultation_id UUID REFERENCES consultations(id) ON DELETE CASCADE,
    patient_id UUID REFERENCES patients(id) ON DELETE CASCADE,
    doctor_id UUID REFERENCES doctors(id) ON DELETE SET NULL,
    medications JSONB NOT NULL DEFAULT '[]',
    notes TEXT,
    is_dispensed BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Patient documents
CREATE TABLE patient_documents (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    clinic_id UUID REFERENCES clinics(id) ON DELETE CASCADE,
    patient_id UUID REFERENCES patients(id) ON DELETE CASCADE,
    consultation_id UUID REFERENCES consultations(id) ON DELETE SET NULL,
    name VARCHAR(300) NOT NULL,
    file_url TEXT NOT NULL,
    file_type VARCHAR(50),
    file_size_kb INTEGER,
    uploaded_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Invoices
CREATE TYPE invoice_status AS ENUM ('DRAFT','ISSUED','PAID','PARTIAL','OVERDUE','CANCELLED','REFUNDED');
CREATE TYPE payment_method AS ENUM ('CASH','MPESA','CARD','INSURANCE','BANK_TRANSFER','ONLINE');
CREATE TABLE invoices (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    clinic_id UUID REFERENCES clinics(id) ON DELETE CASCADE,
    patient_id UUID REFERENCES patients(id) ON DELETE CASCADE,
    appointment_id UUID REFERENCES appointments(id) ON DELETE SET NULL,
    invoice_number VARCHAR(50) UNIQUE NOT NULL,
    issued_at TIMESTAMPTZ DEFAULT NOW(),
    due_date DATE,
    line_items JSONB NOT NULL DEFAULT '[]',
    subtotal DECIMAL(12,2) NOT NULL DEFAULT 0,
    discount DECIMAL(12,2) DEFAULT 0,
    tax DECIMAL(12,2) DEFAULT 0,
    total DECIMAL(12,2) NOT NULL DEFAULT 0,
    paid_amount DECIMAL(12,2) DEFAULT 0,
    status invoice_status DEFAULT 'DRAFT',
    notes TEXT,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE payments (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    clinic_id UUID REFERENCES clinics(id) ON DELETE CASCADE,
    invoice_id UUID REFERENCES invoices(id) ON DELETE CASCADE,
    patient_id UUID REFERENCES patients(id),
    amount DECIMAL(12,2) NOT NULL,
    method payment_method NOT NULL,
    reference VARCHAR(200),
    mpesa_phone VARCHAR(20),
    notes TEXT,
    paid_at TIMESTAMPTZ DEFAULT NOW(),
    recorded_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ DEFAULT NOW()
);
'@
Write-Ok "V1__init_schema.sql"

# V2 — Pharmacy + Lab
Set-Content -Path "$Root\backend\src\main\resources\db\migration\V2__pharmacy_lab.sql" -Encoding UTF8 -Value @'
-- Drugs catalog
CREATE TYPE drug_category AS ENUM ('ANALGESIC','ANTIBIOTIC','ANTIFUNGAL','ANTIVIRAL','ANTIPARASITIC','ANTIHYPERTENSIVE','ANTIDIABETIC','ANTIHISTAMINE','ANTACID','VITAMIN','VACCINE','CONTRACEPTIVE','NSAID','STEROID','OTHER');
CREATE TYPE dispensing_unit AS ENUM ('TABLETS','CAPSULES','SYRUP_ML','INJECTION_VIALS','CREAM_TUBE','DROPS','SACHETS','INHALER','SUPPOSITORY','PATCH');
CREATE TABLE drugs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    clinic_id UUID REFERENCES clinics(id) ON DELETE CASCADE,
    name VARCHAR(300) NOT NULL,
    generic_name VARCHAR(300),
    brand_name VARCHAR(300),
    category drug_category DEFAULT 'OTHER',
    unit dispensing_unit DEFAULT 'TABLETS',
    strength VARCHAR(100),
    description TEXT,
    requires_prescription BOOLEAN DEFAULT TRUE,
    is_controlled BOOLEAN DEFAULT FALSE,
    reorder_level INTEGER DEFAULT 10,
    selling_price DECIMAL(10,2) DEFAULT 0,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE suppliers (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    clinic_id UUID REFERENCES clinics(id) ON DELETE CASCADE,
    name VARCHAR(200) NOT NULL,
    contact_name VARCHAR(200),
    phone VARCHAR(20),
    email VARCHAR(200),
    address TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE drug_batches (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    clinic_id UUID REFERENCES clinics(id) ON DELETE CASCADE,
    drug_id UUID REFERENCES drugs(id) ON DELETE CASCADE,
    supplier_id UUID REFERENCES suppliers(id) ON DELETE SET NULL,
    batch_number VARCHAR(100) NOT NULL,
    quantity_received INTEGER NOT NULL,
    quantity_remaining INTEGER NOT NULL,
    unit_cost DECIMAL(10,2) DEFAULT 0,
    expiry_date DATE NOT NULL,
    received_at TIMESTAMPTZ DEFAULT NOW(),
    received_by UUID REFERENCES users(id),
    notes TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE drug_dispenses (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    clinic_id UUID REFERENCES clinics(id) ON DELETE CASCADE,
    drug_id UUID REFERENCES drugs(id) ON DELETE CASCADE,
    batch_id UUID REFERENCES drug_batches(id) ON DELETE SET NULL,
    consultation_id UUID REFERENCES consultations(id) ON DELETE SET NULL,
    patient_id UUID REFERENCES patients(id) ON DELETE CASCADE,
    prescribed_by UUID REFERENCES users(id),
    dispensed_by UUID REFERENCES users(id),
    quantity INTEGER NOT NULL,
    unit_price DECIMAL(10,2),
    total_price DECIMAL(10,2),
    instructions TEXT,
    dispensed_at TIMESTAMPTZ DEFAULT NOW()
);

-- Lab tests catalog
CREATE TYPE lab_request_status AS ENUM ('REQUESTED','SAMPLE_COLLECTED','PROCESSING','COMPLETED','CANCELLED');
CREATE TYPE lab_priority AS ENUM ('ROUTINE','URGENT','STAT');
CREATE TABLE lab_tests (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    clinic_id UUID REFERENCES clinics(id) ON DELETE CASCADE,
    name VARCHAR(300) NOT NULL,
    code VARCHAR(50),
    category VARCHAR(100),
    description TEXT,
    normal_range TEXT,
    price DECIMAL(10,2) DEFAULT 0,
    turnaround_hours INTEGER DEFAULT 24,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE lab_requests (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    clinic_id UUID REFERENCES clinics(id) ON DELETE CASCADE,
    patient_id UUID REFERENCES patients(id) ON DELETE CASCADE,
    doctor_id UUID REFERENCES doctors(id) ON DELETE SET NULL,
    consultation_id UUID REFERENCES consultations(id) ON DELETE SET NULL,
    request_number VARCHAR(50) UNIQUE,
    status lab_request_status DEFAULT 'REQUESTED',
    priority lab_priority DEFAULT 'ROUTINE',
    tests JSONB NOT NULL DEFAULT '[]',
    total_price DECIMAL(10,2) DEFAULT 0,
    clinical_notes TEXT,
    sample_type VARCHAR(100),
    sample_collected_at TIMESTAMPTZ,
    due_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE lab_results (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    clinic_id UUID REFERENCES clinics(id) ON DELETE CASCADE,
    request_id UUID REFERENCES lab_requests(id) ON DELETE CASCADE,
    test_id UUID REFERENCES lab_tests(id) ON DELETE SET NULL,
    test_name VARCHAR(300) NOT NULL,
    value TEXT,
    unit VARCHAR(50),
    normal_range TEXT,
    is_abnormal BOOLEAN DEFAULT FALSE,
    interpretation TEXT,
    performed_by UUID REFERENCES users(id),
    verified_by UUID REFERENCES users(id),
    result_pdf_url TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- CRM
CREATE TYPE campaign_status AS ENUM ('DRAFT','SCHEDULED','RUNNING','COMPLETED','CANCELLED');
CREATE TABLE crm_campaigns (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    clinic_id UUID REFERENCES clinics(id) ON DELETE CASCADE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    channel comm_channel NOT NULL,
    target_segment JSONB DEFAULT '{}',
    message_template TEXT NOT NULL,
    scheduled_at TIMESTAMPTZ,
    status campaign_status DEFAULT 'DRAFT',
    sent_count INTEGER DEFAULT 0,
    delivered_count INTEGER DEFAULT 0,
    failed_count INTEGER DEFAULT 0,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Chatbot sessions
CREATE TABLE chatbot_sessions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    clinic_id UUID REFERENCES clinics(id) ON DELETE CASCADE,
    channel comm_channel DEFAULT 'WHATSAPP',
    external_id VARCHAR(200),
    patient_id UUID REFERENCES patients(id) ON DELETE SET NULL,
    context JSONB DEFAULT '{}',
    escalated BOOLEAN DEFAULT FALSE,
    escalated_to UUID REFERENCES users(id),
    last_message_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
'@
Write-Ok "V2__pharmacy_lab.sql"

# V3 — Indexes
Set-Content -Path "$Root\backend\src\main\resources\db\migration\V3__indexes.sql" -Encoding UTF8 -Value @'
-- Patient search
CREATE INDEX idx_patients_fulltext ON patients USING GIN (to_tsvector('english', first_name || ' ' || last_name || ' ' || COALESCE(phone, '')));
CREATE INDEX idx_patients_clinic ON patients(clinic_id);
CREATE INDEX idx_patients_phone ON patients(clinic_id, phone);

-- Appointments
CREATE INDEX idx_appointments_date ON appointments(clinic_id, appointment_date, status);
CREATE INDEX idx_appointments_doctor ON appointments(doctor_id, appointment_date);
CREATE INDEX idx_appointments_patient ON appointments(patient_id, appointment_date DESC);

-- Communications
CREATE INDEX idx_reminders_pending ON reminders(send_at, is_sent) WHERE is_sent = FALSE;

-- Audit logs
CREATE INDEX idx_audit_entity ON audit_logs(clinic_id, entity_type, entity_id, created_at DESC);

-- Invoices
CREATE INDEX idx_invoices_patient ON invoices(patient_id, status, created_at DESC);
CREATE INDEX idx_invoices_clinic ON invoices(clinic_id, status);

-- Drug stock
CREATE INDEX idx_drug_batches_drug ON drug_batches(clinic_id, drug_id, expiry_date);
CREATE INDEX idx_drug_batches_expiry ON drug_batches(clinic_id, expiry_date) WHERE quantity_remaining > 0;
CREATE INDEX idx_dispenses_patient ON drug_dispenses(clinic_id, patient_id, dispensed_at DESC);

-- Lab
CREATE INDEX idx_lab_requests_patient ON lab_requests(clinic_id, patient_id, created_at DESC);
CREATE INDEX idx_lab_requests_status ON lab_requests(clinic_id, status, priority);
CREATE INDEX idx_lab_results_request ON lab_results(request_id);

-- Consultations
CREATE INDEX idx_consultations_patient ON consultations(clinic_id, patient_id, created_at DESC);
CREATE INDEX idx_prescriptions_consultation ON prescriptions(consultation_id);
'@
Write-Ok "V3__indexes.sql"

# V4 — Stock view
Set-Content -Path "$Root\backend\src\main\resources\db\migration\V4__stock_view.sql" -Encoding UTF8 -Value @'
CREATE VIEW v_drug_stock AS
SELECT
    d.clinic_id,
    d.id AS drug_id,
    d.name,
    d.generic_name,
    d.category,
    d.unit,
    d.strength,
    d.reorder_level,
    d.selling_price,
    COALESCE(SUM(b.quantity_remaining), 0) AS total_stock,
    COUNT(b.id) FILTER (WHERE b.expiry_date < CURRENT_DATE + INTERVAL ''30 days'' AND b.quantity_remaining > 0) AS batches_expiring_soon,
    MIN(b.expiry_date) FILTER (WHERE b.quantity_remaining > 0) AS nearest_expiry,
    CASE
        WHEN COALESCE(SUM(b.quantity_remaining), 0) = 0 THEN ''OUT_OF_STOCK''
        WHEN COALESCE(SUM(b.quantity_remaining), 0) <= d.reorder_level THEN ''LOW_STOCK''
        ELSE ''IN_STOCK''
    END AS stock_status
FROM drugs d
LEFT JOIN drug_batches b ON b.drug_id = d.id AND b.clinic_id = d.clinic_id
WHERE d.is_active = TRUE
GROUP BY d.clinic_id, d.id, d.name, d.generic_name, d.category, d.unit, d.strength, d.reorder_level, d.selling_price;
'@
Write-Ok "V4__stock_view.sql"

# V5 — Analytics views
Set-Content -Path "$Root\backend\src\main\resources\db\migration\V5__analytics_views.sql" -Encoding UTF8 -Value @'
CREATE MATERIALIZED VIEW mv_daily_appointment_summary AS
SELECT
    clinic_id,
    appointment_date AS report_date,
    COUNT(*) AS total_appointments,
    COUNT(*) FILTER (WHERE status = ''COMPLETED'') AS completed,
    COUNT(*) FILTER (WHERE status = ''NO_SHOW'') AS no_shows,
    COUNT(*) FILTER (WHERE status = ''CANCELLED'') AS cancelled,
    COUNT(*) FILTER (WHERE status IN (''SCHEDULED'',''CONFIRMED'',''CHECKED_IN'',''IN_CONSULTATION'')) AS remaining,
    COUNT(DISTINCT doctor_id) AS active_doctors,
    COUNT(DISTINCT patient_id) AS unique_patients
FROM appointments
GROUP BY clinic_id, appointment_date;

CREATE UNIQUE INDEX ON mv_daily_appointment_summary(clinic_id, report_date);

CREATE VIEW v_patient_lifetime_value AS
SELECT
    p.clinic_id, p.id AS patient_id,
    p.first_name || '' '' || p.last_name AS patient_name,
    p.phone, p.tags,
    COUNT(DISTINCT a.id) AS total_visits,
    MAX(a.appointment_date) AS last_visit_date,
    MIN(a.appointment_date) AS first_visit_date,
    CURRENT_DATE - MAX(a.appointment_date) AS days_since_last_visit,
    COALESCE(SUM(i.paid_amount), 0) AS total_spend,
    CASE
        WHEN COUNT(DISTINCT a.id) >= 10 AND COALESCE(SUM(i.paid_amount),0) > 50000 THEN ''VIP''
        WHEN COUNT(DISTINCT a.id) >= 5 THEN ''LOYAL''
        WHEN COUNT(DISTINCT a.id) >= 2 THEN ''RETURNING''
        ELSE ''NEW''
    END AS patient_segment
FROM patients p
LEFT JOIN appointments a ON a.patient_id = p.id AND a.status = ''COMPLETED''
LEFT JOIN invoices i ON i.patient_id = p.id AND i.status = ''PAID''
GROUP BY p.clinic_id, p.id, p.first_name, p.last_name, p.phone, p.tags;

CREATE VIEW v_at_risk_patients AS
SELECT
    p.clinic_id, p.id AS patient_id,
    p.first_name || '' '' || p.last_name AS patient_name,
    p.phone, p.email,
    MAX(a.appointment_date) AS last_visit,
    CURRENT_DATE - MAX(a.appointment_date) AS days_inactive,
    COUNT(DISTINCT a.id) AS total_visits,
    CASE
        WHEN CURRENT_DATE - MAX(a.appointment_date) BETWEEN 30 AND 60 THEN ''COOLING''
        WHEN CURRENT_DATE - MAX(a.appointment_date) BETWEEN 61 AND 120 THEN ''AT_RISK''
        WHEN CURRENT_DATE - MAX(a.appointment_date) > 120 THEN ''LOST''
    END AS churn_risk
FROM patients p
JOIN appointments a ON a.patient_id = p.id AND a.status = ''COMPLETED''
GROUP BY p.clinic_id, p.id, p.first_name, p.last_name, p.phone, p.email
HAVING CURRENT_DATE - MAX(a.appointment_date) >= 30
ORDER BY days_inactive DESC;
'@
Write-Ok "V5__analytics_views.sql"

# ──────────────────────────────────────────────────────────────
# STEP 5 — Minimal Bootstrap Kotlin
# ──────────────────────────────────────────────────────────────
Write-Step 5 "Writing Kotlin bootstrap files"

Set-Content -Path "$Root\backend\src\main\kotlin\com\cliniq\CliniqApplication.kt" -Encoding UTF8 -Value @'
package com.cliniq

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class CliniqApplication

fun main(args: Array<String>) {
    runApplication<CliniqApplication>(*args)
}
'@
Write-Ok "CliniqApplication.kt"

Set-Content -Path "$Root\backend\src\main\kotlin\com\cliniq\shared\exceptions\Exceptions.kt" -Encoding UTF8 -Value @'
package com.cliniq.shared.exceptions

class NotFoundException(message: String)     : RuntimeException(message)
class ConflictException(message: String)     : RuntimeException(message)
class ForbiddenException(message: String)    : RuntimeException(message)
class BadRequestException(message: String)   : RuntimeException(message)
class UnauthorizedException(message: String) : RuntimeException(message)
'@

Set-Content -Path "$Root\backend\src\main\kotlin\com\cliniq\shared\response\ApiResponse.kt" -Encoding UTF8 -Value @'
package com.cliniq.shared.response

import java.time.Instant

data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: ApiError? = null,
    val meta: PageMeta? = null
) {
    companion object {
        fun <T> success(data: T, meta: PageMeta? = null) = ApiResponse(true, data, meta = meta)
        fun <T> error(code: String, message: String) = ApiResponse<T>(false, error = ApiError(code, message))
    }
}

data class ApiError(val code: String, val message: String?, val details: Any? = null, val timestamp: Instant = Instant.now())
data class PageMeta(val page: Int, val size: Int, val total: Long, val totalPages: Int)
'@
Write-Ok "Shared classes"

# Gradle wrapper
New-Item -ItemType Directory -Path "$Root\backend\gradle\wrapper" -Force | Out-Null
Invoke-WebRequest -Uri "https://raw.githubusercontent.com/gradle/gradle/master/gradle/wrapper/gradle-wrapper.properties" `
    -OutFile "$Root\backend\gradle\wrapper\gradle-wrapper.properties" -ErrorAction SilentlyContinue
Set-Content -Path "$Root\backend\gradle\wrapper\gradle-wrapper.properties" -Value @'
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.10.2-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
'@
Write-Ok "Gradle wrapper config"

# ──────────────────────────────────────────────────────────────
# STEP 6 — Backend Dockerfile
# ──────────────────────────────────────────────────────────────
Write-Step 6 "Writing Dockerfiles"

Set-Content -Path "$Root\backend\Dockerfile" -Encoding UTF8 -Value @'
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY gradle/ gradle/
COPY gradlew build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon -q 2>/dev/null || true
COPY src/ src/
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=5 \
    CMD wget -q --spider http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java","-XX:+UseContainerSupport","-XX:MaxRAMPercentage=75","-jar","/app/app.jar"]
'@
Write-Ok "backend/Dockerfile"

# ──────────────────────────────────────────────────────────────
# STEP 7 — Frontend
# ──────────────────────────────────────────────────────────────
Write-Step 7 "Writing frontend files"

Set-Content -Path "$Root\frontend\package.json" -Encoding UTF8 -Value @'
{
  "name": "cliniq-frontend",
  "version": "1.0.0",
  "private": true,
  "scripts": {
    "dev":   "next dev",
    "build": "next build",
    "start": "next start",
    "lint":  "next lint"
  },
  "dependencies": {
    "next":                    "14.2.5",
    "react":                   "^18.3.1",
    "react-dom":               "^18.3.1",
    "@reduxjs/toolkit":        "^2.2.7",
    "react-redux":             "^9.1.2",
    "@tanstack/react-query":   "^5.51.1",
    "axios":                   "^1.7.2",
    "clsx":                    "^2.1.1",
    "tailwind-merge":          "^2.4.0",
    "lucide-react":            "^0.414.0",
    "react-hot-toast":         "^2.4.1",
    "date-fns":                "^3.6.0"
  },
  "devDependencies": {
    "typescript":              "^5.5.4",
    "@types/node":             "^20.14.12",
    "@types/react":            "^18.3.3",
    "@types/react-dom":        "^18.3.0",
    "tailwindcss":             "^3.4.7",
    "postcss":                 "^8.4.40",
    "autoprefixer":            "^10.4.19",
    "eslint":                  "^8.57.0",
    "eslint-config-next":      "14.2.5"
  }
}
'@

Set-Content -Path "$Root\frontend\tsconfig.json" -Encoding UTF8 -Value @'
{
  "compilerOptions": {
    "target":            "ES2017",
    "lib":               ["dom","dom.iterable","esnext"],
    "allowJs":           true,
    "skipLibCheck":      true,
    "strict":            true,
    "noEmit":            true,
    "esModuleInterop":   true,
    "module":            "esnext",
    "moduleResolution":  "bundler",
    "resolveJsonModule": true,
    "isolatedModules":   true,
    "jsx":               "preserve",
    "incremental":       true,
    "plugins":           [{"name":"next"}],
    "paths":             {"@/*": ["./src/*"]}
  },
  "include":  ["next-env.d.ts","**/*.ts","**/*.tsx",".next/types/**/*.ts"],
  "exclude":  ["node_modules"]
}
'@

Set-Content -Path "$Root\frontend\postcss.config.js" -Encoding UTF8 -Value @'
module.exports = { plugins: { tailwindcss: {}, autoprefixer: {} } }
'@

Set-Content -Path "$Root\frontend\next.config.ts" -Encoding UTF8 -Value @'
import type { NextConfig } from "next"
const nextConfig: NextConfig = {
  output: "standalone",
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${process.env.NEXT_PUBLIC_API_URL}/api/:path*` }]
  },
}
export default nextConfig
'@

Set-Content -Path "$Root\frontend\tailwind.config.ts" -Encoding UTF8 -Value @'
import type { Config } from "tailwindcss"
export default {
  content: ["./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        teal: {
          50:"#f0fdfa",100:"#ccfbf1",200:"#99f6e4",
          400:"#2dd4bf",500:"#14b8a6",600:"#0d9488",
          700:"#0f766e",800:"#115e59",900:"#134e4a"
        }
      }
    }
  },
  plugins: [],
} satisfies Config
'@

Set-Content -Path "$Root\frontend\src\app\globals.css" -Encoding UTF8 -Value @'
@tailwind base;
@tailwind components;
@tailwind utilities;

@layer base {
  body { @apply text-gray-900 antialiased bg-white; }
}
'@

Set-Content -Path "$Root\frontend\Dockerfile" -Encoding UTF8 -Value @'
FROM node:20-alpine AS base
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM node:20-alpine AS runner
WORKDIR /app
ENV NODE_ENV production
RUN addgroup --system --gid 1001 nodejs && adduser --system --uid 1001 nextjs
COPY --from=base --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=base --chown=nextjs:nodejs /app/.next/static ./.next/static
COPY --from=base --chown=nextjs:nodejs /app/public ./public
USER nextjs
EXPOSE 3000
CMD ["node","server.js"]
'@
Write-Ok "Frontend files"

# ──────────────────────────────────────────────────────────────
# STEP 8 — Chatbot
# ──────────────────────────────────────────────────────────────
Write-Step 8 "Writing chatbot files"

Set-Content -Path "$Root\chatbot\package.json" -Encoding UTF8 -Value @'
{
  "name": "cliniq-chatbot",
  "version": "1.0.0",
  "main": "src/index.js",
  "scripts": { "start": "node src/index.js", "dev": "nodemon src/index.js" },
  "dependencies": {
    "express": "^4.19.2",
    "axios": "^1.7.2",
    "redis": "^4.6.14",
    "express-rate-limit": "^7.3.1",
    "helmet": "^7.1.0",
    "morgan": "^1.10.0",
    "winston": "^3.13.0"
  },
  "devDependencies": { "nodemon": "^3.1.4" }
}
'@

Set-Content -Path "$Root\chatbot\Dockerfile" -Encoding UTF8 -Value @'
FROM node:20-alpine
WORKDIR /app
COPY package*.json ./
RUN npm ci --only=production
COPY . .
EXPOSE 5000
HEALTHCHECK --interval=30s CMD wget -q --spider http://localhost:5000/health || exit 1
CMD ["node","src/index.js"]
'@
Write-Ok "Chatbot files"

# ──────────────────────────────────────────────────────────────
# STEP 9 — Root docker-compose + .env
# ──────────────────────────────────────────────────────────────
Write-Step 9 "Writing docker-compose and .env"

Set-Content -Path "$Root\.env" -Encoding UTF8 -Value @'
DB_NAME=cliniq
DB_USER=cliniq
DB_PASSWORD=cliniq_dev_2024
REDIS_PASSWORD=redis_dev_2024
JWT_SECRET=cliniq_jwt_secret_minimum_64_chars_long_change_in_production_please_now
SPRING_PROFILES_ACTIVE=dev
AT_API_KEY=
AT_USERNAME=sandbox
MPESA_CONSUMER_KEY=
MPESA_CONSUMER_SECRET=
MPESA_SHORTCODE=174379
MPESA_PASSKEY=
MPESA_CALLBACK_URL=http://localhost:8080/api/v1/billing/mpesa/callback
MPESA_SANDBOX=true
WHATSAPP_PHONE_ID=
WHATSAPP_ACCESS_TOKEN=
WHATSAPP_VERIFY_TOKEN=dev_verify_token_change_me
DEFAULT_CLINIC_SLUG=demo
FRONTEND_URL=http://localhost:3000
NEXT_PUBLIC_API_URL=http://localhost:8080
NODE_ENV=development
LOG_LEVEL=info
'@
Write-Ok ".env (API keys blank — fill before using SMS/M-Pesa)"

# Copy docker-compose if it exists in Desktop downloads
$DownloadedCompose = "$env:USERPROFILE\Desktop\docker-compose.yml"
if (Test-Path $DownloadedCompose) {
    Copy-Item $DownloadedCompose "$Root\docker-compose.yml" -Force
    Write-Ok "docker-compose.yml (copied from downloads)"
}

# .gitignore
Set-Content -Path "$Root\.gitignore" -Encoding UTF8 -Value @'
.env
*.class
*.jar
build/
.gradle/
node_modules/
.next/
dist/
*.log
.DS_Store
Thumbs.db
'@
Write-Ok ".gitignore"

# README
Set-Content -Path "$Root\README.md" -Encoding UTF8 -Value @'
# ClinIQ — Clinic Management Platform

## Quick start (development)
```bash
docker-compose up -d
```
- Backend:  http://localhost:8080/actuator/health
- Frontend: http://localhost:3000
- DB:       localhost:5432

## Test the API
```powershell
.\Test-ClinIQ.ps1
```

## Copy domain code
See the CLINIQ_*.kt files downloaded from the build session.
Place each file in backend/src/main/kotlin/com/cliniq/domain/{module}/

## Deploy to Hetzner
```bash
DOMAIN=your-domain.com EMAIL=you@email.com bash setup.sh
```
'@
Write-Ok "README.md"

# ──────────────────────────────────────────────────────────────
# STEP 10 — Copy downloaded files to correct locations
# ──────────────────────────────────────────────────────────────
Write-Step 10 "Locating downloaded build files"

$Downloads = @(
    @{ File = "CLINIQ_AUTH_MODULE.kt";               Dest = "backend\src\main\kotlin\com\cliniq\domain\auth" },
    @{ File = "cliniq-backend-core.kt";              Dest = "backend\src\main\kotlin\com\cliniq\domain" },
    @{ File = "CLINIQ_ANALYTICS.sql";                Dest = "backend\src\main\resources\db\migration\_reference" },
    @{ File = "CLINIQ_PHARMACY_MODULE.kt";           Dest = "backend\src\main\kotlin\com\cliniq\domain\pharmacy" },
    @{ File = "CLINIQ_LAB_CONSULTATION_MODULES.kt";  Dest = "backend\src\main\kotlin\com\cliniq\domain" },
    @{ File = "CLINIQ_BILLING_MODULE.kt";            Dest = "backend\src\main\kotlin\com\cliniq\domain\billing" },
    @{ File = "CLINIQ_ENTITY_FIXES.kt";              Dest = "backend\src\main\kotlin\com\cliniq" },
    @{ File = "CLINIQ_MISSING_PIECES.kt";            Dest = "backend\src\main\kotlin\com\cliniq" },
    @{ File = "CLINIQ_CHATBOT.js";                   Dest = "chatbot\src" },
    @{ File = "CLINIQ_FRONTEND_COMPLETE.tsx";        Dest = "frontend\_reference" },
    @{ File = "Test-ClinIQ.ps1";                     Dest = "." },
    @{ File = "setup.sh";                            Dest = "devops" }
)

$DesktopPath = "$env:USERPROFILE\Desktop"
$Copied = 0
foreach ($f in $Downloads) {
    $src = "$DesktopPath\$($f.File)"
    if (Test-Path $src) {
        $destDir = "$Root\$($f.Dest)"
        New-Item -ItemType Directory -Path $destDir -Force | Out-Null
        Copy-Item $src $destDir -Force
        Write-Ok "Copied: $($f.File) → $($f.Dest)"
        $Copied++
    } else {
        Write-Inf "Not found on Desktop (download from chat): $($f.File)"
    }
}
Write-Ok "$Copied / $($Downloads.Count) files located and copied"

# ──────────────────────────────────────────────────────────────
# STEP 11 — npm install (frontend)
# ──────────────────────────────────────────────────────────────
Write-Step 11 "Installing frontend dependencies"
if (Get-Command npm -ErrorAction SilentlyContinue) {
    Push-Location "$Root\frontend"
    npm install --silent 2>&1 | Select-String "added|warn|error" | Write-Inf
    Pop-Location
    Write-Ok "npm install complete"
} else {
    Write-Inf "npm not found — run 'npm install' in frontend/ manually after installing Node.js"
}

# ──────────────────────────────────────────────────────────────
# STEP 12 — Docker Compose up
# ──────────────────────────────────────────────────────────────
Write-Step 12 "Starting Docker services"
if (-not $SkipDocker -and (Get-Command docker-compose -ErrorAction SilentlyContinue)) {
    Push-Location $Root
    Write-Inf "Starting db + redis first (30s)..."
    docker-compose up -d db redis 2>&1 | Out-Null
    Start-Sleep 15
    Write-Inf "Starting backend + chatbot..."
    docker-compose up -d backend chatbot 2>&1 | Out-Null
    Start-Sleep 5
    Write-Inf "Starting frontend..."
    docker-compose up -d frontend 2>&1 | Out-Null
    Pop-Location
    Write-Ok "Docker services started"
    Write-Inf "Backend takes ~90s to be ready (Flyway runs migrations)"
} else {
    Write-Inf "Skipped (run: docker-compose up -d  from $Root)"
}

# ──────────────────────────────────────────────────────────────
# Summary
# ──────────────────────────────────────────────────────────────
Write-Host ""
Write-Host "  ╔══════════════════════════════════════════╗" -ForegroundColor Green
Write-Host "  ║   ✅ ClinIQ setup complete!              ║" -ForegroundColor Green
Write-Host "  ╚══════════════════════════════════════════╝" -ForegroundColor Green
Write-Host ""
Write-Host "  Project:  $Root" -ForegroundColor White
Write-Host ""
Write-Host "  URLS (wait ~90s for backend):" -ForegroundColor Cyan
Write-Host "  → Frontend:  http://localhost:3000" -ForegroundColor White
Write-Host "  → Backend:   http://localhost:8080/actuator/health" -ForegroundColor White
Write-Host "  → API docs:  http://localhost:8080/swagger-ui.html" -ForegroundColor White
Write-Host ""
Write-Host "  TEST:" -ForegroundColor Cyan
Write-Host "  → cd $Root && .\Test-ClinIQ.ps1" -ForegroundColor White
Write-Host ""
Write-Host "  NEXT — copy domain modules from downloaded .kt files:" -ForegroundColor Cyan
Write-Host "  Files are in: $Root\backend\src\main\kotlin\com\cliniq\" -ForegroundColor White
Write-Host "  See: $Root\backend\_reference\ for full module code" -ForegroundColor White
Write-Host ""
Write-Host "  LOGS:" -ForegroundColor Cyan
Write-Host "  → docker-compose -f $Root\docker-compose.yml logs -f backend" -ForegroundColor White
Write-Host ""

# 📱 Digital Munshi - 100% Offline-First Native Android ERP / POS

[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20%28API%2026--34%29-blue.svg)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Kotlin-2.0.20-purple.svg)](https://kotlinlang.org)
[![UI](https://img.shields.io/badge/Jetpack%20Compose-Material%203-green.svg)](https://developer.android.com/jetpack/compose)
[![Security](https://img.shields.io/badge/SQLCipher-256--bit%20AES-red.svg)](https://www.zetetic.net/sqlcipher/)
[![Zero-Internet](https://img.shields.io/badge/Internet%20Permission-None%20%28100%25%20Air--Gapped%29-brightgreen.svg)](#zero-internet-architectural-guarantee)
[![Build](https://img.shields.io/badge/Release%20APK-16.3%20MB-success.svg)](#compiled-release-apk)

**Digital Munshi** is an enterprise-grade, 100% offline-first native Android Point of Sale (POS) and Enterprise Resource Planning (ERP) application engineered for retail stores, supermarkets, grocery merchants, and wholesalers. 

Designed with an **air-gapped, zero-internet architecture**, it guarantees total data sovereignty, instantaneous transaction latency, and hardware peripheral control without relying on any external cloud or network connectivity.

---

## 🛡️ Zero-Internet Architectural Guarantee

The Android Manifest strictly omits `android.permission.INTERNET`:
- **Zero Remote Leakage**: The app cannot make any HTTP, WebSocket, or remote telemetry requests.
- **Local Machine Learning**: Barcode scanning is handled completely on-device using bundled Google ML Kit models and ZXing Core.
- **Air-Gapped Encrypted Backups**: Backups are written directly to user-selected local folders via the Android Storage Access Framework (SAF).
- **Offline Reminders**: Khata balance reminders invoke native SIM-card SMS (`smsto:`) or local WhatsApp application intents without network dependencies.

---

## 🏗️ Technical Stack & Architecture

- **Architecture**: Clean Architecture (Domain, Data, Presentation) + MVVM with Kotlin Coroutines & `StateFlow`.
- **Database Layer**: Room with **SQLCipher (256-bit AES encryption)**, fully normalized relational schema, foreign key cascades, and atomic `@Transaction` checkout routines.
- **UI & Dual Displays**: Jetpack Compose Material 3 with multi-display support powered by the **Android `Presentation` API** (for dual-screen POS hardware like Sunmi, Posiflex, Clover, and external HDMI monitors).
- **Hardware Integration**:
  - Direct ESC/POS byte-protocol engine for 58mm and 80mm thermal receipt printers (USB-OTG & Bluetooth SPP).
  - Cash drawer kick pulse generator (`0x1B, 0x70, 0x00, 0x19, 0xFA`).
  - USB-Serial driver for continuous electronic weighing scales (Essae, Toledo, Avery Berkel).
  - Physical USB/Bluetooth HID wedge barcode scanner interceptor.
- **Cryptographic Security**: PBKDF2WithHmacSHA256 (100,000 rounds) key derivation, AES-256-GCM authenticated encryption, and SQLite `PRAGMA integrity_check;`.

---

## 🚀 Key Modules & Capabilities

### 1. POS & Fractional Billing Engine
- **Fractional Decimal Weights**: Full precision support for weighed goods (e.g., `1.450 kg`).
- **Dynamic Tier Switching**: Unit price automatically shifts from `retailPrice` to `wholesalePrice` when cart item quantity reaches `wholesaleMinQty`.
- **Dynamic NPCI UPI QR Generator**: Generates compliant UPI deep-links (`upi://pay?pa=...&am=...&cu=INR`) and renders crisp monochrome QR bitmaps for on-screen modals, customer display mirrors, and thermal receipts.
- **Atomic Checkout**: Simultaneously decrements batch & product inventory, verifies credit limits, writes invoice line items, and commits audit logs in a single ACID transaction.

### 2. Hardware & Peripherals Suite
- **Multi-Printer ESC/POS Driver**: Formats bold headers, tabular line items, CGST/SGST tax breakdown, and printed dynamic UPI QR bit-images (`GS v 0`).
- **Customer-Facing Display**: Secondary display extending `android.app.Presentation` showing live cart items, grand total, and live dynamic QR for quick customer self-checkout.
- **Continuous Scale Stream Parser**: Automatically reads and parses stable weights (`ST,GS,+  1.450kg`) over USB-Serial.

### 3. Khata Ledgers & Debt Governance
- **Credit Limit Enforcement**: Prohibits credit purchases if customer balance exceeds `maxCreditLimit`.
- **Single-Tap Offline Reminders**: Creates pre-filled SMS and WhatsApp messages with current balance, invoice reference, and merchant UPI payment link.

### 4. Inventory Batch Printer & Expiry Radar
- **Sticky-Label PDF Engine**: Generates offline A4 printable PDF sheets in 24, 30, and 40 label grid configurations with Code128 barcodes, batch numbers, MRP, and expiry dates.
- **Expiry Radar Screen**: Queries batches where `expiryDate <= now + thresholdDays` (7, 15, 30, 60 days) with at-risk inventory value calculations.

### 5. Role-Based Access & Cashier Security Lock
- **PIN Authentication**: PBKDF2 hashed PINs with random salts.
- **Cashier Lock**: Masks cost prices, net margins, day-close sales reports, manual price overrides, and refunds unless authenticated with Admin PIN.
- **Tamper-Evident Audit Log**: Logs sales, refunds, item deletions, and zero-value drawer kicks.

### 6. WhatsApp-Style Encrypted Local Backups
- **Dual Profiles**:
  - Profile 1 (Lean): Database + Preferences.
  - Profile 2 (Full): Database + Invoices + Product Images.
- **AES-256-GCM Streaming**: Encrypted ZIP stream with 4-byte magic header (`DMB1`), 16-byte random salt, 12-byte IV, and 128-bit authentication tag.
- **Integrity Validation & Rollback Protection**: Throws `AEADBadTagException` on incorrect PIN; runs SQLCipher `PRAGMA integrity_check;` in a sandbox, and stages a rollback backup before atomic database replacement.

---

## 📦 Compiled Release APK

- **File Path**: `app/build/outputs/apk/release/app-release.apk`
- **Size**: **16.3 MB** (`17,152,571 bytes`)
- **Optimization Strategy**:
  - R8 full code minification & dead code elimination.
  - AAPT2 unused resource shrinking.
  - Localization pruning (`en` only).
  - Native ABI filtering to ARM (`armeabi-v7a`, `arm64-v8a`), stripping unnecessary x86 binaries.

---

## 🛠️ Build & Installation

### Prerequisites
- JDK 17
- Android SDK (API 34)

### Build Commands
```bash
# Compile and assemble the release APK
./gradlew assembleRelease

# Run automated unit tests
./gradlew testReleaseUnitTest
```

### Install on Device via ADB
```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

---

## 📄 License
Commercial / Proprietary POS & ERP Engine. Designed for Digital Munshi.

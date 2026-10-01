# Game Turbo - Release & In-Place Update Guide / دليل النشر وتحديث التطبيق المباشر

---

## English Guide

### 1. Generating Your Permanent Release Keystore (ONCE ONLY)
Generate your permanent upload keystore using the command:
```bash
keytool -genkeypair -v -keystore my-upload-key.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
```
> ⚠️ **CRITICAL SECURITY WARNING:**
> Back up your `my-upload-key.jks` file and its passwords in at least two separate, secure places (e.g., encrypted cloud storage + offline USB drive).
> **If this keystore file or its password is lost or replaced, Android will permanently refuse all future updates over existing installations.**

---

### 2. Verifying Signature Match Before Publishing
Android enforces that updates must have the exact same signing certificate. Before publishing any new APK, compare its certificate signature with the currently installed or previously published APK using `apksigner`:
```bash
apksigner verify --print-certs old.apk
apksigner verify --print-certs new.apk
```
Look for the `Signer #1 certificate SHA-256 digest` output. **Both lines must be 100% identical.**
If the fingerprints differ, Android will reject the update with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.

---

### 3. Testing In-Place Update on Device (`adb install -r`)
To test upgrading over an existing installation without losing user data, run:
```bash
adb install -r Game-Turbo-v2.apk
```
Common adb errors:
- `INSTALL_FAILED_UPDATE_INCOMPATIBLE`: The new APK was signed with a different key than the one on the device.
- `INSTALL_FAILED_VERSION_DOWNGRADE`: The `versionCode` of the new APK is not strictly greater than the currently installed one.

---

### 4. Pre-Release Checklist
Before releasing any update, verify:
- [ ] **Same Keystore**: Built using the permanent `my-upload-key.jks` (same SHA-256 certificate digest).
- [ ] **Higher versionCode**: `APP_VERSION` in `version.properties` increased by 1 (e.g. 1 -> 2).
- [ ] **Unchanged applicationId**: Stays `com.aistudio.pubgbooster.remix` (no suffixes).
- [ ] **In-Place Upgrade Verified**: Tested on a real device with `adb install -r` and verified that Room database, game list, logs, snapshots, and preferences remain intact.
- [ ] **Release Notes Updated**: `RELEASE_NOTES.md` edited with the new version changes.

---

### 5. One-Time Uninstall Note
If a user or tester currently has an old debug build or an APK signed with an ad-hoc key, Android will block the new official release. The user must perform a **one-time uninstall** of the old non-production build. After installing the official release, all future updates will install seamlessly in-place over it.

---

### 6. Automated Publishing via GitHub Actions
1. Encode your `my-upload-key.jks` to base64:
   ```bash
   base64 -w 0 my-upload-key.jks
   ```
2. In GitHub repository **Settings > Secrets and variables > Actions**, add:
   - `KEYSTORE_BASE64`: The full base64 string.
   - `STORE_PASSWORD`: Keystore password.
   - `KEY_PASSWORD`: Key password.
   - `KEY_ALIAS`: `upload` (optional, defaults to `upload`).
3. To publish version N:
   - Update `APP_VERSION` in `version.properties` (e.g. `2`).
   - Update `RELEASE_NOTES.md`.
   - Commit, push, tag and push:
     ```bash
     git add version.properties RELEASE_NOTES.md
     git commit -m "Release v2"
     git push origin main
     git tag v2
     git push origin v2
     ```
   The workflow `.github/workflows/release.yml` will automatically build, verify, sign, and publish **Game Turbo v2** with `Game-Turbo-v2.apk`.

---

## الدليل باللغة العربية (Arabic Guide)

### 1. إنشاء مفتاح التوقيع الدائم (يُنشأ مرة واحدة فقط)
أنشئ ملف المفاتيح الدائم عبر سطر الأوامر:
```bash
keytool -genkeypair -v -keystore my-upload-key.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
```
> ⚠️ **تحذير أمني شديد الأهمية:**
> احفظ نسخة احتياطية من ملف `my-upload-key.jks` وكلمات المرور في مكانين آمنين على الأقل (مثل وحدة تخزين سحابية مشفرة + ذاكرة فلاش offline).
> **في حال فقدان هذا المفتاح، سيرفض نظام أندرويد نهائياً تثبيت أي تحديث قادم فوق النسخة القديمة، وسيُجبر المستخدمون على حذف التطبيق وفقدان بياناتهم.**

---

### 2. مطابقة التوقيع الرقمي قبل النشر
يشترط أندرويد تطابق شهادة التوقيع لقبول التحديث. قارن توقيع النسخة القديمة بالجديدة عبر أداة `apksigner`:
```bash
apksigner verify --print-certs old.apk
apksigner verify --print-certs new.apk
```
تحقق من سطر `SHA-256 digest`؛ **يجب أن يكون السطران متطابقين تماماً بحرف بحرف.** إذا اختلف التوقيع، سيرفض الهاتف التثبيت فوراً بخطأ `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.

---

### 3. اختبار التحديث المباشر على الهاتف (`adb install -r`)
لاختبار التثبيت فوق النسخة الحالية دون فقدان البيانات:
```bash
adb install -r Game-Turbo-v2.apk
```
معاني أخطاء التثبيت الشائعة:
- `INSTALL_FAILED_UPDATE_INCOMPATIBLE`: التطبيق الجديد موقع بمفتاح مختلف عن المفتاح المثبت على الهاتف.
- `INSTALL_FAILED_VERSION_DOWNGRADE`: رقم `versionCode` في التحديث الجديد ليس أعلى من النسخة المثبتة.

---

### 4. قائمة التحقق قبل النشر (Pre-release Checklist)
قبل إرسال التحديث للمستخدمين:
- [ ] **نفس المفتاح الدائم**: الحزمة موقعة بنفس ملف `my-upload-key.jks`.
- [ ] **رقم الإصدار أعلى**: زاد رقم `APP_VERSION` في `version.properties` بمقدار 1.
- [ ] **ثبات معرف التطبيق**: المعرف لا يزال `com.aistudio.pubgbooster.remix`.
- [ ] **اختبار التحديث**: تم التثبيت بـ `adb install -r` فوق النسخة السابقة وتأكيد بقاء قاعدة البيانات والإعدادات وسجلات الجلسات كما هي.
- [ ] **تحديث الملاحظات**: كتابة جديد الإصدار في `RELEASE_NOTES.md`.

---

### 5. تنبيه الحذف لمرة واحدة (One-Time Uninstall)
إذا كان هاتف المستخدم مثبتاً عليه حالياً نسخة تجريبية (Debug) أو حزمة موقعة بمفتاح عشوائي سابق، يجب حذف تلك النسخة القديمة لمرة واحدة فقط وتثبيت هذه النسخة الموقعة بالمفتاح الدائم. بعد ذلك، ستثبت كافة التحديثات القادمة فوقها مباشرة وبسلاسة تامة.

# Releasing Game Turbo / دليل نشر وتحديث التطبيق

---

## English Guide

### Automated Release (Recommended via GitHub Actions)
1. Edit `version.properties` in the project root: change `APP_VERSION` to the next whole number (e.g. `1` -> `2`).
2. Update `RELEASE_NOTES.md` with the new changes under `# Game Turbo v<N>`.
3. Commit and push the changes:
   ```bash
   git add version.properties RELEASE_NOTES.md
   git commit -m "Prepare release v2"
   git push origin main
   ```
4. Create and push the corresponding git tag:
   ```bash
   git tag v2
   git push origin v2
   ```
5. GitHub Actions workflow `.github/workflows/release.yml` will automatically verify that the tag matches `version.properties`, build the signed release APK `Game-Turbo-v2.apk`, and publish the GitHub Release titled **"Game Turbo v2"**.

---

### Required GitHub Secrets
To allow GitHub Actions to sign the release APK, configure these repository secrets in **Settings > Secrets and variables > Actions**:
1. `KEYSTORE_BASE64`: Base64 string of your keystore file. Generate with:
   ```bash
   base64 -w 0 my-upload-key.jks
   ```
2. `STORE_PASSWORD`: The keystore password.
3. `KEY_PASSWORD`: The private key password.

---

### Manual Release Alternative
If you prefer building and publishing manually:
1. Provide the signing environment variables locally and run:
   ```bash
   KEYSTORE_PATH=/path/to/my-upload-key.jks STORE_PASSWORD="your_store_pass" KEY_PASSWORD="your_key_pass" ./gradlew assembleRelease
   ```
2. The generated APK will be at `app/build/outputs/apk/release/Game-Turbo-v<N>.apk`.
3. On GitHub: go to **Releases > Draft a new release**:
   - Tag: `v<N>` (e.g. `v2`)
   - Release title: `Game Turbo v<N>` (e.g. `Game Turbo v2`)
   - Description: Copy from `RELEASE_NOTES.md`
   - Upload asset: `Game-Turbo-v<N>.apk`
   - Click **Publish release**.

---

### ⚠️ Critical Android Warnings
- **Version Number**: Android enforces that updates can only be installed if `versionCode` is strictly higher than the installed version. The version number must always increase (1 -> 2 -> 3).
- **Keystore Consistency**: You must ALWAYS use the exact same keystore and key alias for all releases. If a release is signed with a different key, Android will refuse to update the installed app with the error *"App not installed as package appears to be corrupt / signature mismatch"*, requiring users to uninstall and lose their data.

---

## الدليل باللغة العربية (Arabic Guide)

### النشر التلقائي عبر GitHub Actions (الطريقة الموصى بها)
1. افتح ملف `version.properties` في جذر المشروع، وغيّر رقم `APP_VERSION` إلى الرقم التالي مباشرة (مثلاً من `1` إلى `2`).
2. اكتب ملاحظات التحديث الجديد داخل ملف `RELEASE_NOTES.md` تحت العنوان `# Game Turbo v2`.
3. احفظ التغييرات وادفعها للفرع الرئيسي:
   ```bash
   git add version.properties RELEASE_NOTES.md
   git commit -m "Prepare release v2"
   git push origin main
   ```
4. أنشئ الـ Tag وارفعه إلى GitHub:
   ```bash
   git tag v2
   git push origin v2
   ```
5. سيبدأ سير عمل GitHub Actions تلقائياً بالتحقق من تطابق الـ Tag مع ملف `version.properties`، ثم بناء الحزمة الموقعة `Game-Turbo-v2.apk` ونشر الإصدار بعنوان **"Game Turbo v2"**.

---

### أسرار GitHub المطلوبة (GitHub Secrets)
لتوقيع التطبيق تلقائياً في GitHub Actions، أضف الأسرار التالية في مستودع GitHub من **Settings > Secrets and variables > Actions**:
1. `KEYSTORE_BASE64`: تشفير ملف مفتاح التوقيع Base64. يمكنك إنشاؤه عبر الأمر:
   ```bash
   base64 -w 0 my-upload-key.jks
   ```
2. `STORE_PASSWORD`: كلمة مرور ملف الـ Keystore.
3. `KEY_PASSWORD`: كلمة مرور المفتاح الخاص داخل الـ Keystore.

---

### طريقة النشر اليدوي (البديلة)
إذا أردت البناء والنشر بنفسك:
1. مرر متغيرات بيئة التوقيع وشغّل أمر البناء في جهازك:
   ```bash
   KEYSTORE_PATH=/path/to/my-upload-key.jks STORE_PASSWORD="your_store_pass" KEY_PASSWORD="your_key_pass" ./gradlew assembleRelease
   ```
2. ستجد ملف الحزمة الناتج في المسار: `app/build/outputs/apk/release/Game-Turbo-v<N>.apk`.
3. على GitHub: ادخل إلى **Releases > Draft a new release**:
   - اسم الـ Tag: `v<N>` (مثل `v2`)
   - عنوان الإصدار: `Game Turbo v<N>` (مثل `Game Turbo v2`)
   - الوصف: انسخ محتوى `RELEASE_NOTES.md`
   - ارفع الملف: `Game-Turbo-v<N>.apk`
   - اضغط **Publish release**.

---

### ⚠️ تنبيهات هامة لنظام أندرويد
- **رقم الإصدار**: يشترط نظام أندرويد أن يكون `versionCode` أعلى دائماً من النسخة المثبتة على الهاتف حتى يقبل التحديث، لذا يجب زيادة الرقم دائماً (1 ثم 2 ثم 3).
- **ملف المفاتيح (Keystore)**: يجب الحفاظ على نفس ملف المفتاح `my-upload-key.jks` ونفس كلمات المرور لكافة التحديثات القادمة. تغيير المفتاح سيجعل هواتف المستخدمين ترفض تثبيت التحديث لوجود تضارب في التوقيع الرقمي.

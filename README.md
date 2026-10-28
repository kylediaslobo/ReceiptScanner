# Receipt Scanner

A personal Android app for phones and tablets that fills in the monthly Entertainment & Expense Claims form. You photograph a receipt in the app and it reads the date and amount. You add the nature of expense and the company (earlier values are one tap away), check the names, and tap the green **Save** button. Once a month you export that month's receipts to the claim form in Excel, then choose whether to delete or keep them.

**Install:** open this link on your Android phone, download the APK, and allow your browser to install apps when asked:
https://github.com/kylediaslobo/ReceiptScanner/releases/latest/download/ReceiptScanner.apk

## How it fits together

| Part | What it uses | Cost |
|---|---|---|
| App | Kotlin, Jetpack Compose, CameraX (in-app camera). On wide screens, the list and a receipt show side by side. | Free |
| Account, data, photos | Supabase free tier: email login, Postgres with row-level security, a private storage bucket | Free |
| Reading receipts | The `extract-receipt` Edge Function sends the photo to Claude Haiku 5.5 and gets back JSON in a fixed shape | About $0.001–0.002 per scan |
| Photo cleanup | Every night, pg_cron runs the `cleanup-images` Edge Function. It keeps your 15 newest photos that are under 14 days old and deletes the rest. | Free |
| Excel export | The claim form as an .xlsx, built on the phone for one month and shared through Android's share sheet | Free |
| APK builds | GitHub Actions builds every push. A `v*` tag publishes a GitHub Release. | Free (public repo) |

Lost a scan? Open **⋮ → Recent photos** and tap a photo to read it again. Your name for the form is under **⋮ → Your name**.

## One-time setup

### 1. Supabase project
1. Create a free project at supabase.com.
2. **Authentication → Users → Add user**: create your own account.
3. **Authentication → Sign In / Providers**: turn off **Allow new users to sign up**. Anyone with the APK has your anon key, so this stops strangers from signing up.
4. Copy `local.properties.example` to `local.properties` and fill in `SUPABASE_URL` and `SUPABASE_ANON_KEY` from **Project Settings → API**.

### 2. Database, functions and secrets
Run these from the repo root. `login` and `link` ask you questions in the terminal.

```bash
npx supabase@2.122.0 login
```

```bash
npx supabase@2.122.0 link --project-ref YOUR-PROJECT-REF
```

```bash
npx supabase@2.122.0 db push
```

1. Copy `supabase/functions/.env.example` to `supabase/functions/.env`.
2. Fill in your Anthropic API key and a long random `CLEANUP_SECRET`.
3. Upload the secrets and deploy both functions:

```bash
npx supabase@2.122.0 secrets set --env-file supabase/functions/.env
```

```bash
npx supabase@2.122.0 functions deploy
```

### 3. Image cleanup schedule
The nightly job reads two values from Supabase Vault. In the dashboard **SQL Editor**, run the following once. Use your own URL and the same `CLEANUP_SECRET` as in `.env`:

```sql
select vault.create_secret('https://YOUR-PROJECT-REF.supabase.co', 'project_url');
select vault.create_secret('YOUR-CLEANUP-SECRET', 'cleanup_secret');
```

### 4. GitHub build secrets
The APK signing key lives in `~/.receipt-scanner-signing/`, outside the repo. **Back up that folder.** Every update has to be signed with the same key, or Android won't install it over the existing app.

```bash
bash scripts/set-github-secrets.sh
```

### 5. Publish a version

```bash
git tag v1.0.0 && git push origin v1.0.0
```

The workflow builds the APK and attaches it to a GitHub Release. The install link above always points at the newest release.

## Excel layout
`app/src/main/java/com/kyled/receiptscanner/export/ReceiptExport.kt` copies the "dsb with formulae" sheet of the claims template:
- Name and Month Ending
- section A: Date, Nature of Expense, Company, Names and Amount, in Rand, with at least 18 rows, plus the sub total formula
- section B (motor car) and the totals, speedometer, signature and authorisation blocks, left blank to fill in by hand

Each CI run uploads a sample form as the `sample-claim-form` artifact.

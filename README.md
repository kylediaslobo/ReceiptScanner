# Receipt Scanner

A personal Android app for phones and tablets. You photograph a receipt in the app, it pulls out the details, you check them, and you save the receipt to your account. You can also export receipts to Excel.

## Stack
- **App:** Kotlin, Jetpack Compose and CameraX. It's installed by sideloading an APK.
- **Backend:** Supabase free tier, which provides auth, Postgres, Storage, Edge Functions and Cron.
- **Extraction:** a Supabase Edge Function calls Claude Haiku with vision.

## Setup
1. Open the project in Android Studio and let Gradle sync.
2. Copy `local.properties.example` to `local.properties` and fill in your Supabase URL and anon key.
3. Copy `supabase/functions/.env.example` to `supabase/functions/.env` and add your Anthropic API key.

Never commit `local.properties`, `.env` or the signing keystore. `.gitignore` already excludes them.

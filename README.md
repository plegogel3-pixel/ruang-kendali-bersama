# Ruang Kendali Bersama

A transparent, consent-based Android family safety project. Parent and Child apps share Firebase/Cloudflare services.

## Status
Initial repository scaffold. No device testing or production Firebase/LiveKit configuration has been completed.

## Modules
- `parent-app`: parent dashboard starter
- `child-app`: child onboarding/status starter
- `cloudflare-workers`: API worker starter
- `firebase`: Firestore rules and indexes
- `.github/workflows`: CI build

## Build
GitHub Actions builds debug APKs on push/dispatch. Release builds require owner-configured signing secrets and Firebase configuration as applicable. Never commit service-account keys, LiveKit API secrets, or signing keys.

## Setup required
1. Create Firebase project; enable Authentication and Firestore, register both Android package IDs.
2. Add valid `google-services.json` to each app (not committed).
3. Configure Cloudflare Worker secrets via dashboard/wrangler secrets.
4. Configure LiveKit project and issue short-lived tokens from Worker.
5. Review Firestore rules before production.

Android limits: ordinary apps cannot prevent Force Stop or uninstall. Location, usage access, app restrictions, and background execution require explicit user/system-granted permissions and vary by Android/OEM.

## Pairing prototype (6-digit)
The Parent and Child Compose screens now call the Worker `/pair/redeem` and `/pair/create` routes with Firebase ID tokens. The screens include an explicit **test-only anonymous Firebase session** button; this is not a production parent/family account system.

### Configure local build
Edit both app Gradle files and replace:
- `WORKER_BASE_URL` with the deployed HTTPS Worker URL
- `FIREBASE_API_KEY`, `FIREBASE_APP_ID`, `FIREBASE_PROJECT_ID` with the values from your Firebase Android app

In Firebase Console enable **Authentication → Sign-in method → Anonymous** for testing. Do not enable production pairing until you implement durable parent accounts, family membership, child-side consent confirmation, and the server-side ownership checks.

### Deploy pairing Worker (not yet deployed)
1. Create Cloudflare D1 database and apply `cloudflare-workers/pairing-schema.sql`.
2. Install dependencies in `cloudflare-workers`; configure `PAIR_DB`, `FIREBASE_PROJECT_ID`, and Worker secret `PAIR_HMAC_KEY`.
3. Deploy the Worker and set its HTTPS URL in both Gradle files.
4. Check the Worker implementation and perform security/race-condition tests before real family data is used.

Current pairing commits are code scaffolding. No APK, deployed Worker, Firebase project, or end-to-end success is claimed yet.

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

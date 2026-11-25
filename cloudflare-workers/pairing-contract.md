# Six-digit pairing contract
- Child authenticated with Firebase requests a code for its registered device.
- Code is uniformly random, six decimal digits (including leading zeroes), valid 5 minutes, single-use.
- Parent authenticates, enters code, reviews child/device identity, and confirms. Child app also displays linked confirmation.
- POST /pair/create {deviceId}; POST /pair/redeem {code}; POST /pair/revoke {deviceId}. All require Firebase bearer token.
- Server verifies Firebase token signature, issuer, audience and expiry; UID comes only from verified token.
- Store keyed HMAC hash, never plaintext. Atomic conditional claim, 5 wrong attempts, edge rate limiting, no-store responses.
- Generic failure for invalid/expired/consumed codes. Revoke only a link owned by the authenticated parent.
- D1 binding PAIR_DB; Worker secret PAIR_HMAC_KEY; var FIREBASE_PROJECT_ID.
This is implementation groundwork, not yet deployed or app-integrated. Review rate limiting and race/transaction behavior before production.

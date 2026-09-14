# Face challenge — frontend-first (Nivel B)

`POST /api/face/challenge/start` issues a single-use `challengeId` token with `enabled` and `expiresInMs`.

`type` and `instruction` in `ChallengeStartResponse` are deprecated. The frontend chooses turn direction locally; those fields remain in the JSON for old clients only.

Token validation is unchanged: `consume()` on verify, `isValid()` on identify when `face.challenge.require-for-identify=true`.

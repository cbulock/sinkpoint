# Google Play releases

The private [Build private AAB workflow](https://github.com/cbulock/sinkpoint-builds/actions/workflows/build-aab.yml) automatically prepares the newest `main` commit whose public APK workflow has passed. It polls at minutes 17 and 47 each hour; GitHub can delay scheduled runs. If several commits arrive between checks, only the current main commit is prepared. Use **Run workflow** to check immediately. A pending or failed main build is deferred.

When enabled, it runs unit tests, release lint, and the release bundle build, signs using the existing registered upload key, verifies the certificate, archives the bundle/mapping/checksum/version, and publishes only to `com.cbulock.sinkpoint`'s `internal` track. A completed internal release becomes available to enrolled testers; Google can still require review. It does not change production, listings, tester lists, or managed publishing.

Android embeds the R8 mapping in the AAB; Google Play [extracts it automatically](https://support.google.com/googleplay/android-developer/answer/9848633?hl=en-419). The publisher checks that the archived mapping matches those embedded bytes and does not upload a duplicate mapping.

## One-time setup

Install `scripts/private-builds/build-aab.yml` as `.github/workflows/build-aab.yml` in **private** `cbulock/sinkpoint-builds`. Source code, including `scripts/publish_play.py`, must be on public `main` first. The workflow refuses a public destination. The public APK workflow continues independently.

### Upload signing key

Use the upload keystore used for the existing app. Do not create a replacement key. Compare its SHA-256 certificate fingerprint with **Protected with Play > Play Store protection > Manage Play app signing > Upload key certificate** in Play Console. The upload certificate is distinct from Google's app signing certificate.

Configure these Actions **secrets in `sinkpoint-builds`**:

| Secret | Value |
| --- | --- |
| `PLAY_UPLOAD_KEYSTORE_BASE64` | Base64 of the binary upload keystore |
| `PLAY_UPLOAD_STORE_PASSWORD` | Keystore password |
| `PLAY_UPLOAD_KEY_ALIAS` | Upload key alias |
| `PLAY_UPLOAD_KEY_PASSWORD` | Private key password |
| `PLAY_SERVICE_ACCOUNT_JSON` | Full downloaded service account JSON |

Set the Actions **variable** `PLAY_UPLOAD_CERT_SHA256` to the upload certificate's SHA-256 fingerprint (colon-separated or plain hex). Credentials are passed through environment variables; passwords are never command-line values. The temporary keystore is removed after signing. Keep the original keystore and its saved credential file backed up outside Git.

### Google API account

1. Select or create a Google Cloud project and [enable the Google Play Android Developer API](https://console.cloud.google.com/apis/library/androidpublisher.googleapis.com).
2. Create a dedicated service account such as `sinkpoint-play-uploader`. It does not need a project Owner or Editor role.
3. In [Play Console Users and permissions](https://play.google.com/console/u/0/developers/4930466446317624327/users-and-permissions), add that service account's email, grant access to **Sinkpoint only**, and grant **View app information (read-only)** and **Release apps to testing tracks**. Do not grant production release permission. If Play requires an invitation confirmation, complete it in the UI.
4. Create a JSON key for that service account and store the entire JSON as `PLAY_SERVICE_ACCOUNT_JSON` in the private repository. For a downloaded file, use `Get-Content -Raw <file> | gh secret set PLAY_SERVICE_ACCOUNT_JSON --repo cbulock/sinkpoint-builds` in PowerShell. Do not paste JSON into chat or commit it. Restrict local access to the downloaded key.
5. In Play Console **Test and release > Testing > Internal testing > Testers**, add your tester account/list and use its opt-in link. This workflow does not manage testers.
6. Compare the computed `:app:printAppVersion` code with the latest version already uploaded to Play. It must increase for a new bundle; retain main history and adjust the version baseline deliberately if needed.
7. Set `PLAY_UPLOAD_ENABLED=true` as an Actions **variable** in `sinkpoint-builds`, then run **Build private AAB** manually for the first verification. Confirm the version appears in Internal testing and installs through Play.

Google's [API setup guide](https://developers.google.com/android-publisher/getting_started) describes the account setup; its billing-specific permissions are unnecessary for this app. Once enabled, missing credentials fail clearly. Until enabled, the workflow continues unsigned private bundle storage and reports that Play setup is pending.

## Updates, retries, and failures

- Push changes to public `main`. Once checks pass, the next private workflow run uploads the newest main build automatically.
- A private release's `upload-ready.json` marks an archived, signed build ready for upload. Retries restore those exact bytes, verify the checksum and signing certificate, and reuse the version. They do not rebuild a potentially different bundle with the same version code.
- `play-internal.json` is written only after the Google API confirms publication. Existing private unsigned releases do not block the first signed upload.
- A rerun also checks Play directly. If that identical bundle is already completed on internal, it succeeds without another upload. A newer version anywhere in Play or different contents under the same version code fails instead of overwriting it.
- Missing permission/API errors are reported and do not create a success marker. Inspect **Actions > failed run** and correct the cause, then rerun. If Play requires review, follow its console instructions; the workflow does not silently change review behavior.
- Set `PLAY_UPLOAD_ENABLED=false` to pause Google Play uploads while keeping private builds. Keep **Build private AAB** as the only publisher for this app; concurrent edits in Play Console may invalidate an API edit and require a retry.

## Production

After installing and testing the internal build, promote that existing release to **Production** in Play Console, supply user-facing release notes, and submit it for review. No rebuild is necessary. Optionally enable [managed publishing](https://support.google.com/googleplay/android-developer/answer/9859654?hl=en) to choose when an approved production update goes live; internal testing is not held by that setting.

## Verification

Publisher tests need only Python's standard library:

```powershell
python -m unittest discover -s scripts -p test_publish_play.py -v
```

They cover new upload, mapping, internal-only track changes, reuse/retry, draft completion, version rollback, checksum mismatch, and API failures. The public APK workflow runs these tests before publishing. The private workflow installs pinned `google-auth` and `requests` dependencies only for real uploads. Live permission, Google review, tester enrollment, and device installation require the actual Play account.

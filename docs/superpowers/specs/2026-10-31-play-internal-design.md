# Sinkpoint Google Play deployment

Port the established Minescape release process requested in the linked chat:
private bundle backups and automatic internal testing uploads after successful
public main checks, with manual production promotion in Play Console.

Use the same tested publisher and private workflow with Sinkpoint package,
repository, and asset names. Preserve preview signing and use the existing
registered upload key; the user confirmed it is shared with Minescape.
Credentials belong only in private `cbulock/sinkpoint-builds` Actions secrets.
Never alter Minescape's deployment or grant the uploader production access.

APK and AAB versions derive from full Git history. Anchor the current source at
version 0.1.0/code 1002, the latest preview code; verify this exceeds Play's
existing versions before enabling uploads. Subsequent commits increment both.

Mirror polling every 30 minutes, successful exact-source checks, certificate
verification, private mapping/checksum/version backups, identical-bundle retries,
and rejection of version rollback or different contents under an existing code.
Document setup, pausing, retries, and manual production promotion.

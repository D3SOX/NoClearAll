# Release signing

Official APKs use a dedicated PKCS#12 key. Private signing material is stored under
`signing/private/`, which is excluded from Git.

The tag-release workflow expects these GitHub Actions secrets:

- `NOCLEARALL_SIGNING_KEY_BASE64`
- `NOCLEARALL_KEYSTORE_PASSWORD`
- `NOCLEARALL_KEY_ALIAS`
- `NOCLEARALL_KEY_PASSWORD`

The local backup consists of `noclearall-release.p12` and GPG-encrypted `credentials.asc`. Back up
both files securely. To inspect the recovery credentials locally:

```bash
gpg --decrypt signing/private/credentials.asc
```

Never commit the private directory or print secret values into CI logs.

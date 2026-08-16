# TwinKey backup format

This document defines the `.twinkey` backup file, byte for byte. The iOS/macOS app and
the Android app must both produce **identical bytes** for an identical list of accounts.

Anything not stated here is not part of the format and must not appear in a backup.

## 1. File

| | |
|---|---|
| Extension | `.twinkey` |
| UTType (Apple) | `ru.artembolotov.twinkey.backup` |
| MIME | `application/json` |
| Encoding | UTF-8, no BOM |
| Content | A single JSON array, compact (no spaces, no newlines, no trailing newline) |

The file is **not** encrypted — it carries raw secrets and must be treated as sensitive.

## 2. JSON

```json
[{"url":"otpauth://totp/Issuer:name?algorithm=SHA1&digits=6&issuer=Issuer&period=30","secret":"JBSWY3DPEHPK3PXP…"}]
```

Rules:

1. The top level is an array of objects, one per account, in the order the accounts are
   shown in the app.
2. Each object has exactly two keys, in this order: `url`, then `secret`. Write the pair
   from an ordered structure, never from a hash map, and never with a key-sorting option
   (`JSONEncoder.OutputFormatting.sortedKeys` and its equivalents would emit
   `secret`, `url`).
3. `secret` is the raw shared secret (the decoded bytes, **not** Base32) encoded as
   standard Base64 with padding, per RFC 4648 §4 — the `+/` alphabet, not the URL-safe one.
4. The secret is carried **only** in this field. It must never be added to the URL as a
   `secret=` query item.
5. Solidus is **not** escaped: write `"a/b"`, never `"a\/b"`. Both are valid JSON, but
   only one of them can be the canonical form.
   *Foundation escapes it by default — `JSONEncoder.outputFormatting` must include
   `.withoutEscapingSlashes`.*
6. A well-formed backup is pure ASCII: every non-ASCII byte is percent-encoded inside
   the URL (§3.1), and the secret is Base64. A writer must therefore never need `\uXXXX`
   escapes, and must not emit them.

## 3. The `url` field

```
otpauth://totp/<label>?algorithm=<ALG>&digits=<N>&issuer=<issuer>&period=<P>
```

* Scheme is always `otpauth`, host is always `totp`. **HOTP is not supported** — a
  backup must never contain `otpauth://hotp/…`, and a reader rejects such an entry.
* `<label>` is `<issuer>:<name>`, with an **unencoded** colon between the two parts.
  If the issuer is empty, the label is `<name>` alone, with no leading colon.
* Query items appear in exactly this order: `algorithm`, `digits`, `issuer`, `period`.
  All four are always present, even when the value is a default; `issuer=` may be empty.
* `<ALG>` is `SHA1`, `SHA256` or `SHA512` (uppercase). `<N>` and `<P>` are decimal
  integers without a sign or leading zeros; `period` is in seconds.

### 3.1 Percent-encoding

The issuer and the name are percent-encoded **separately**, and only then joined with the
literal `:`. The same encoding is applied to the `issuer` query value.

* Leave unescaped only the RFC 3986 *unreserved* set: `A–Z a–z 0–9 - . _ ~`
* Escape every other byte as `%XX`, over the UTF-8 bytes, with **uppercase** hex digits.
* Consequences worth spelling out, because they are the usual source of divergence:
  * a space is `%20` — **never** `+`;
  * a plus sign is `%2B` — it never survives as a bare `+`;
  * `@` is `%40`, and a colon inside a name or an issuer is `%3A`.

Escaping a colon inside a name is what keeps the label unambiguous: the only unencoded
colon in a label is the separator.

> **Do not use form encoding** (`application/x-www-form-urlencoded`,
> `java.net.URLEncoder`, `URLSearchParams`) anywhere. It writes a space as `+`, which
> collides with a real `+` in the same file — see §5.

### 3.2 Examples

| name | issuer | url |
|---|---|---|
| `teeemon@gmail.com` | `Google` | `otpauth://totp/Google:teeemon%40gmail.com?algorithm=SHA1&digits=6&issuer=Google&period=30` |
| `teeemon@gmail.com` | `DigitalPlat Domains` | `otpauth://totp/DigitalPlat%20Domains:teeemon%40gmail.com?algorithm=SHA1&digits=6&issuer=DigitalPlat%20Domains&period=30` |
| `+79051533275` | `Госуслуги` | `otpauth://totp/%D0%93%D0%BE%D1%81%D1%83%D1%81%D0%BB%D1%83%D0%B3%D0%B8:%2B79051533275?algorithm=SHA1&digits=6&issuer=%D0%93%D0%BE%D1%81%D1%83%D1%81%D0%BB%D1%83%D0%B3%D0%B8&period=30` |
| `na:me` | `Issuer` | `otpauth://totp/Issuer:na%3Ame?algorithm=SHA1&digits=6&issuer=Issuer&period=30` |
| `user` | *(empty)* | `otpauth://totp/user?algorithm=SHA1&digits=6&issuer=&period=30` |

## 4. Reading

A reader must be permissive, because backups written by earlier versions are still out
there and must keep restoring correctly. Given a `url`:

1. Take the label as it appears in the file, **before** percent-decoding.
2. If it contains an unencoded `:`, split at the **first** one — that is the canonical
   form; the left part is the issuer, the right part is the name.
3. Otherwise percent-decode the whole label and, if it starts with `<issuer>:` where
   `<issuer>` is the value of the `issuer` query item, strip that prefix. This covers
   both legacy shapes: a bare name (old iOS) and `issuer%3Aname` (old Android).
4. The `issuer` query item wins over the issuer taken from the label; the label is only
   a fallback for a file with no `issuer` item.
5. Missing query items fall back to `SHA1` / `6` / `30`.
6. Reject the entry if the secret is missing or the host is not `totp`.

Note that step 3 is decode-then-compare, so a legacy reader that percent-decodes the
label first and splits on the first colon also reads canonical files correctly.

## 5. Why `+` is banned, and what it costs

Backups written by the Android app before this specification form-encoded the label and
the issuer, so a space became `+`. In one and the same file this produced:

```
otpauth://totp/DigitalPlat+Domains%3Ateeemon%40gmail.com?…&issuer=DigitalPlat+Domains
otpauth://totp/%D0%93%D0%BE%D1%81%D1%83%D1%81%D0%BB%D1%83%D0%B3%D0%B8%3A+79051533275?…
```

The first `+` means a space, the second is a real plus in a phone number, and nothing in
the file distinguishes them. A reader that expands `+` to a space corrupts the phone
number; a reader that keeps it corrupts the issuer name. **This is unrecoverable**, so no
reader tries to guess: such files import with a literal `+` in the issuer, and the user
can rename the account. Only the display name is affected — the secret, and therefore
every generated code, is intact.

## 6. Reference implementation

iOS/macOS: `TwinKey/OneTimePassword/Token+URL.swift` (`urlForToken`, `token(from:)`) and
`TwinKey/Models/BackupDocument.swift`.

Android, the encoder that matters:

```kotlin
private const val UNRESERVED =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

/** RFC 3986 percent-encoding. Not URLEncoder: that one writes a space as '+'. */
fun percentEncode(value: String): String = buildString {
    for (byte in value.toByteArray(Charsets.UTF_8)) {
        val char = byte.toInt().toChar()
        if (char in UNRESERVED) append(char)
        else append('%').append("%02X".format(byte.toInt() and 0xFF))
    }
}

fun toUri(name: String, issuer: String, algorithm: String, digits: Int, period: Int): String {
    val label =
        if (issuer.isEmpty()) percentEncode(name)
        else percentEncode(issuer) + ":" + percentEncode(name)
    return "otpauth://totp/$label" +
        "?algorithm=$algorithm&digits=$digits&issuer=${percentEncode(issuer)}&period=$period"
}
```

Serialize the array with a JSON writer that does not escape `/` (Gson escapes it —
`GsonBuilder().disableHtmlEscaping()`; kotlinx.serialization and Moshi do not) and that
keeps the `url`, `secret` key order.

## 7. Checking two implementations against each other

Export the same accounts, in the same order, from both apps and compare the bytes:

```bash
cmp ios.twinkey android.twinkey && echo "identical"
```

Anything other than "identical" is a bug in one of the writers.

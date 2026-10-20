# Security Policy

## Supported versions

Security fixes go into the latest release and the `main` branch.

| Version | Supported |
|---|---|
| Latest release | ✅ |
| Older releases | ❌ |

## Reporting a vulnerability

**Please do not open a public issue for security problems.**

Use GitHub's private reporting instead:

1. Go to the [Security tab](https://github.com/Someshog/NeverMore/security) of this repository.
2. Click **Report a vulnerability** and describe the problem.

Helpful details to include:

- What the issue is and what an attacker could do with it
- Steps to reproduce, or a proof of concept
- The app version and Android / WebView version
- Any suggested fix

You can expect an acknowledgement within about **7 days**. This is a volunteer-run project, so fix times vary, but we'll keep you updated and credit you in the release notes if you'd like.

## Scope

Examples of what we consider in scope:

- Ways for a web page to escape the WebView sandbox through NeverMore's code (for example through an exposed JavaScript interface or unsafe intent handling)
- Unsafe handling of downloaded filter lists or scripts (for example code execution from a tampered list)
- Leaks of cookies, tokens, or other private data
- Vulnerabilities in the native (Rust) library

Out of scope:

- Vulnerabilities in third-party sites or in Android System WebView itself (report those to the vendor)
- YouTube or other services detecting or restricting ad blocking
- Ads that still appear (please use a normal bug report)

## Design notes

- Remote filter lists are loaded with scriptlet permissions **disabled**, because they aren't authenticated Brave component payloads.
- The ad-pruning script is bundled in the app and only injected into YouTube origins.

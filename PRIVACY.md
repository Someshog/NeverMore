# Privacy

*Last updated: October 2026.*

NeverMore is built to respect your privacy. This page explains what the app does and doesn't do with data. It describes the code in this repository. If you install a build from somewhere else, you are trusting that build's publisher.

## What NeverMore does not do

- It has **no analytics, no tracking, no ads of its own, and no crash reporting**.
- It has **no user accounts** and does not ask for your name, email, or location.
- It does not collect, store, or send your browsing history, watch history, or the pages you open to the NeverMore developers. There are no NeverMore servers.

## What the app connects to

| Connection | Why | What is sent |
|---|---|---|
| Filter list hosts: `easylist.to`, `raw.githubusercontent.com` (uBlock Origin, Brave lists) | Download and refresh ad-blocking rules (about once every 24 hours) | A normal HTTPS request, which exposes your IP address to those hosts, like any download |
| The websites you open (for example `youtube.com`, `twitch.tv`) | To show you the site | Normal web traffic, exactly as in any browser |

Network requests that match the filters are blocked on your device and never leave it.

## What is stored on your device

- **Filter lists** are cached in the app's private storage so the app can start quickly and work offline.
- **Website data** such as cookies, local storage and cache is stored by the Android WebView in the app's private storage, as in any browser. This is how sites keep you signed in. Signing in happens on the site itself. NeverMore never sees your password.

To remove all of it, clear the app's data in Android **Settings → Apps → NeverMore → Storage → Clear data**, or uninstall the app.

## Third-party services

Sites you open have their own privacy policies, and they apply when you use them through NeverMore. NeverMore's blocking reduces tracking but cannot make a site stop tracking you entirely, especially if you are signed in.

## Changes and questions

If this policy changes, the new version will be committed to this repository, so the full history is public. For questions, [open an issue](https://github.com/Someshog/NeverMore/issues).

# Dupe Digger privacy note

Dupe Digger finds duplicate files on your device. All of the work happens on the device itself.

## What the app reads

- **Files you point it at.** It reads only what you allow:
  - photos, videos and audio, if you grant media access;
  - every file, if you turn on Deep scan (All files access);
  - folders you pick yourself.
- **Contents of those files.** It reads them to compare them:
  - content hashes for exact copies;
  - picture fingerprints for similar photos and videos;
  - text fingerprints for documents;
  - tags and lengths for audio;
  - with the optional AI model, an on-device embedding of each file.

## What the app keeps

The app keeps these in its private storage on the device:

- an index of the files it scanned (names, paths, sizes, dates, hashes and embeddings);
- your choices (which copy to keep, groups marked "not duplicates", excluded folders);
- your settings;
- files you sent to Quarantine;
- any AI model you downloaded.

None of it is included in Google cloud backup or device-to-device transfer. Uninstalling the app removes all of it.

## What leaves the device

**Your files and the data above never leave the device.** The app has no account, no analytics, no ads and no crash reporting.

The one network connection is the **optional AI model download**:

- **When:** only when you tap Download on the AI matches screen. By default it waits for Wi-Fi.
- **What it fetches:** an EmbeddingGemma model file from Hugging Face (`huggingface.co/litert-community/…`), over HTTPS. The app checks the file against a fixed SHA-256 checksum before using it.
- **What it sends:** a plain file request. If a download was interrupted, the request also says which byte to resume from. No file names, file contents, results or identifiers are sent. As with any download, Hugging Face can see your IP address and standard request headers.

Without the AI model, the app makes no network connections at all.

## Deleting files

The app never deletes anything on its own. You choose what to remove, and it always keeps at least one file in every group.

- Photos, videos and audio go to the system trash, where they stay for 30 days.
- Other files move to Quarantine, where you can restore them or delete them for good.

## Google Play Data safety

The app collects no user data and shares none.

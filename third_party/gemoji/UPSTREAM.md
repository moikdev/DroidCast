# Offline emoji catalog

Source: https://github.com/github/gemoji

Pinned revision: `fadaeaf1f1a9be82b321316a6c5502e43138b2f6`

`emoji.json` is the unmodified `db/emoji.json` from that revision. Its MIT
license is retained as `LICENSE` and bundled as `app/src/main/assets/GEMOJI-LICENSE`.
Copyright (c) 2019 GitHub, Inc.

Run `python3 scripts/generate-emoji.py` to regenerate
`app/src/main/assets/emoji-catalog.tsv`: 1,870 emoji plus 70 common text symbols
authored for Droidcast. No emoji images are distributed; Android's font renders
the Unicode strings. Multi-codepoint strings, flags, variation selectors and
joiners are retained without normalization. This snapshot does not promise the
latest Unicode set or every Unicode symbol. Search annotations are English.

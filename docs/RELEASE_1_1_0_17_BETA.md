# Photon 1.1.0.17 Beta

- Remove the fixed blue/purple hero palette. Home now uses the selected Material/Prism primary, tertiary and surface containers with matching readable foreground colours, including light themes.
- Replace Liked Music's all-liked-video classification pipeline with the account's native YouTube Music Liked Songs list (VLLM). One source list, pagination and cache; no individual watch-page metadata lookups, category guesses, custom playlist, or second login.
- Read Music-host cookies from the existing WebView cookie store, pinned to the same selected Google account. Music requests are read-only; cookie selection and request-origin signatures remain host-specific. Existing YouTube mutations/playback reporting remain unchanged.
- Native-list cache schema 3 replaces previously inferred music entries on migration. Cached native songs open instantly; recent-list refresh and periodic reconciliation use the native list. Unlikes remove immediately; new likes enter when returned by Google's song list, rather than guessing from a title/category.
- Accept only the requested native playlist shelf/continuation, not Music Home suggestions. Unsupported/authentication failures retain already loaded native songs and offer retry; they do not silently revert to scanning all likes.

Hosted validation pending. Private-account/physical-device verification has not been performed. This follows what YouTube Music includes in its Liked Songs list, which may differ from all Music-category YouTube likes. If the existing session cannot access YouTube Music, the app reports the failure rather than creating another login/session.

# Neue Frutiger Arabic (optional premium font)

Drop the licensed font files here to activate the premium Arabic typeface:

    public/fonts/neue-frutiger-arabic/NeueFrutigerArabic-Regular.woff2
    public/fonts/neue-frutiger-arabic/NeueFrutigerArabic-Medium.woff2

These files are served as static assets (copied verbatim, NOT processed by the
bundler), so the build never fails if they are absent — the @font-face simply
resolves to a runtime 404 and the stack falls back to IBM Plex Sans Arabic
(self-hosted, always present) and then to system Arabic fonts.

No code changes are needed after adding the files — just rebuild/redeploy.

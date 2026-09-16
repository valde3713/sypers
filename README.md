# Sypers og Syper Family

En mobilvenlig sikkerhedsapp til at tjekke og blokere usikre telefonnumre.

## Start lokalt

```bash
python3 -m http.server 8000
```

Åbn derefter `http://localhost:8000/sypers_login.html`.

Download-side for håndholdte Android-enheder:

```text
/sypers.fam
```

Sypers er hovedappen. Syper Family er den separate pårørende-app med
engangsparring, batteristatus og indbygget kort.

## Installer på Android uden Play Store

Åbn Sypers via HTTPS på mobilen, for eksempel:

```text
https://catnap-frill-jolliness.ngrok-free.dev/sypers.html
```

I Chrome: tryk på menuen med de tre prikker og vælg **Føj til startskærm**
eller **Installer app**. Sypers åbner derefter som en selvstændig app.

Ngrok og den lokale server kan startes sådan fra projektmappen:

```bash
python3 -m http.server 8000 --bind 127.0.0.1
/home/vald3713/.local/bin/ngrok http 8000
```

Ngrok-adressen kan ændre sig, hvis tunnelen oprettes på ny. Brug en fast,
HTTPS-server i produktion.

## Android-projekter

- `sypers-android/` bygger hovedappen **Sypers**.
- `sypers-family-android/` bygger den separate **Syper Family**-app.

GPS-parring oprettes fra Sypers, indtastes én gang i Syper Family og kan
fjernes fra Family-appen.

## Offline og online

Åbn appen online første gang, så service worker-filerne gemmes på telefonen.
Derefter virker login, profil, indstillinger og lokale blokerede numre også
offline. Online bruges til at hente den nyeste version, når internet er
tilgængeligt.

iPhone/iPad understøtter ikke APK. Åbn download-siden i Safari og vælg
**Del → Føj til hjemmeskærm**.

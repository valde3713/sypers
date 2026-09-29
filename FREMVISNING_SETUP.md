# Sypers Fremvisning Setup

## Ngrok Konfiguration
**Ngrok Token:** `3JJFUVLn0nTEPfqy9zllnor6v9i_75hSfeDShJ9TDwHHKsrWk`

## Start Kommandoer

### 1. Start Python Server
```bash
cd sypers
python3 -m http.server 8000
```

### 2. Start Ngrok Tunnel
```bash
ngrok config add-authtoken 3JJFUVLn0nTEPfqy9zllnor6v9i_75hSfeDShJ9TDwHHKsrWk
ngrok http 8000 --log=stdout
```

## URL'er til Fremvisning

**ngrok URL:** https://catnap-frill-jolliness.ngrok-free.dev

**Alle sider via ngrok:**
- Sypers Login: https://catnap-frill-jolliness.ngrok-free.dev/sypers_login.html
- Sypers Main: https://catnap-frill-jolliness.ngrok-free.dev/sypers.html
- Syper Family Download: https://catnap-frill-jolliness.ngrok-free.dev/syper-family-download.html
- Track Page: https://catnap-frill-jolliness.ngrok-free.dev/track.html
- Sypers Profile: https://catnap-frill-jolliness.ngrok-free.dev/sypers_profile.html

**Lokal test:**
- http://localhost:8000/sypers_login.html
- http://localhost:8000/sypers.html
- http://localhost:8000/syper-family-download.html
- http://localhost:8000/track.html
- http://localhost:8000/sypers_profile.html

## Instruktioner til Android Fremvisning

1. Åbn ngrok-URL'en på Android-telefon
2. I Chrome: Tryk på menu (tre prikker)
3. Vælg "Føj til startskærm" eller "Installer app"
4. Appen installeres som selvstændig app på hjemmeskærmen
5. Virker offline efter første besøg

## APK Ændringer (Udført)

**Fjernet fra AndroidManifest.xml:**
- Call Screening Service
- READ_PHONE_STATE permission
- ANSWER_PHONE_CALLS permission

**Fjernet fra MainActivity.java:**
- `onBackPressed()` metode
- Call screening role request funktioner

**Slettet fil:**
- SypersCallScreeningService.java

**Nedgraderet build:**
- Android Gradle Plugin: 8.5.2 → 4.2.2
- Gradle: 8.5 → 6.7.1
- compileSdk: 35 → 33
- targetSdk: 35 → 33

**Bemærk:** APK bygning kræver Android SDK for at blive færdiggjort. Web PWA fungerer nu som erstatning.

## Project Location
**Mappe:** `C:\Users\vald3713\.codeium\.devin\sypers`

## Vigtige Filer

### APK Status
- **sypers.apk** - Eksisterer stadig (GAMLE version før ændringer)
- **Ny APK** - Kan ikke bygges uden Android SDK
- **Løsning** - Brug Web PWA til fremvising

### Tracking Status
- **track.html** - Kræver server backend API som ikke er implementeret
- **Mangler API endpoints:** `/api/login`, `/api/pair/confirm`, `/api/pair/unpair`, `/api/location`
- **Kan ikke bruges** til fremvisning uden server backend
- **Login:** sypers_login.html
- **Main:** sypers.html  
- **Download:** syper-family-download.html
- **Track:** track.html
- **Profile:** sypers_profile.html

## Genereret af Devin
Dato: 28-09-2026

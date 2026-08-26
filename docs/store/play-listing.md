# Play Store listing — Aura (Android)

Everything to paste into Play Console for the internal-testing listing. Copy adapted from the iOS App Store / askmira.es copy, rewritten for the Android port: phone only, home-screen widget only (no Apple Watch, no iPad, no Lock Screen widgets — those don't exist here, so I dropped them rather than promising them).

## Assets (in this folder)

- App icon (512×512): `play-listing-icon-512.png`
- Feature graphic (1024×500): `feature-graphic-1024x500.png`
- Phone screenshots (1200×2400, 2:1, no alpha): `screenshots/1_hero.png` … `5_wind.png`

## App details

- **App name (title, ≤30):** `Aura · El tiempo`
- **Default language:** Spanish (Spain) — es-ES. Add English (US) as a second listing language.
- **App or game:** App
- **Category:** Weather
- **Free / Paid:** Free
- **Tags:** Weather, Maps & Navigation (pick Weather as primary)

## Contact details (Store settings)

- **Email:** dears-respect.8u@icloud.com
- **Website:** https://askmira.es/aura
- **Phone:** (optional, leave blank)

## URLs

- **Privacy policy:** https://askmira.es/aura/privacidad
- **Support / website:** https://askmira.es/aura/soporte

---

## Short description (≤80 chars)

**ES:** `El tiempo de España con datos de AEMET. Privada, sin cuenta ni anuncios.`

**EN:** `Spain's weather from official AEMET data. Private, no account, no ads.`

---

## Full description (≤4000 chars)

### ES

Aura lee AEMET —la Agencia Estatal de Meteorología— a través de la API OpenData y tu propia clave gratuita, y lo lleva donde la app oficial nunca llegó: una pantalla clara, un widget en la pantalla de inicio y avisos que coinciden con tu municipio. Sin servidores, sin cuenta, sin telemetría: tu teléfono es el centro.

Una pantalla Hoy con criterio

Hoy abre con un titular redactado en el propio teléfono, en español claro, y luego una pila completa de tarjetas: condiciones actuales, la franja horaria, la previsión a siete días, el arco amanecer→ocaso, el viento, la calidad del aire (ICA de MITECO), el índice UV, el radar más cercano y el boletín oficial de AEMET, todo sobre un cielo que sigue al sol en directo.

Qué incluye

• Predicción de AEMET por horas y a siete días
• Titular en lenguaje natural, redactado en el dispositivo
• Cielo vivo que cambia con el tiempo y la hora del día
• Avisos oficiales (CAP) de AEMET, asignados a tu municipio por provincia
• Temperatura realmente observada en la estación de AEMET más cercana
• Calidad del aire por contaminante (ICA de MITECO)
• Índice UV, con escala y franja de protección
• Viento con escala de Beaufort
• Amanecer, ocaso y horas de luz
• Radar de precipitación de la región más cercana
• Boletín autonómico redactado por AEMET
• Noticias meteorológicas de fuentes públicas (RSS)
• Widget del tiempo en la pantalla de inicio

Privada por diseño

Tu clave de AEMET se guarda cifrada en el propio teléfono, nunca en el binario, el repositorio ni un servidor. No hay «nube de Aura»: la app guarda una instantánea local que lee el widget, y nada sale de tu dispositivo. Sin cuenta, sin registro, sin seguimiento, sin analíticas y sin anuncios. Modo oscuro y claro adaptables.

Gratis y de código abierto (MIT). Elaborado con datos de AEMET.

Necesitas una clave gratuita de AEMET OpenData (opendata.aemet.es). Se introduce una vez y vive solo en tu teléfono.

### EN

Aura reads AEMET — Spain's national weather service — through the OpenData API and your own free key, then puts it where the official app never did: a clear screen, a home-screen widget, and official warnings matched to your town. No backend, no account, no telemetry — your phone is the hub.

An editorial Hoy screen

Hoy opens on a headline written on the phone itself, in plain Spanish, then a full card stack: current conditions, the hourly strip, a seven-day outlook, a sunrise→sunset arc, wind, air quality (MITECO ICA), the UV index, the nearest radar, and the official AEMET bulletin — all over a live sun-tracking sky.

What's inside

• AEMET forecast, hourly and seven-day
• Natural-language headline, composed on-device
• A living sky that changes with the weather and the time of day
• Official AEMET warnings (CAP), matched to your town by province
• Real observed temperature from the nearest AEMET station
• Air quality by pollutant (MITECO ICA)
• UV index, with scale and protection window
• Wind with the Beaufort scale
• Sunrise, sunset and hours of daylight
• Precipitation radar for the nearest region
• Regional bulletin written by AEMET
• Weather news from public sources (RSS)
• A home-screen weather widget

Private by design

Your AEMET key is stored encrypted on the phone itself — never in the binary, the repo, or a server. There is no "Aura cloud": the app keeps a local snapshot the widget reads, and nothing leaves your device. No account, no sign-up, no tracking, no analytics, no ads. Adaptive dark and light.

Free and open source (MIT). Made with AEMET data.

You'll need a free AEMET OpenData key (opendata.aemet.es). You enter it once and it lives only on your phone.

---

## Data safety form (App content → Data safety)

Aura collects and shares **no** user data. Answer the wizard accordingly:

- Does your app collect or share any of the required user data types? **No.**
- Is all of the user data encrypted in transit? **Yes** (the app only makes HTTPS calls to public services).
- Do you provide a way for users to request that their data is deleted? **Not applicable / no data collected.**

Note on location: Aura requests only coarse (approximate) location, used **on the device** to find the nearest municipality, and only while the app is in use. The municipality code — never the device's actual position — is what's sent to the public services. Because nothing is collected, stored off-device, or linked to an identity, this is **not** "data collection" in the Data safety sense. (This matches https://askmira.es/aura/privacidad.)

## Other App-content declarations

- **Privacy policy:** https://askmira.es/aura/privacidad
- **App access:** All functionality is available without special access; no login. (You do enter a free AEMET key in Settings, but there is no account to log into — note this in the "instructions" box if asked.)
- **Ads:** No ads.
- **Content rating (IARC questionnaire):** utility/weather app; no objectionable content → expect Everyone / PEGI 3.
- **Target audience:** 18+ (or 13+); **not** designed for children. "Appeals to children" = No.
- **News app:** No, this is not a news app (the RSS section is a minor feature).
- **Government app / Financial features / Health:** No to all.
- **Data safety:** see above.

## Store settings

- **Country/region availability:** Spain (at minimum). Add others if you want.
- **Ads:** Contains ads = No.

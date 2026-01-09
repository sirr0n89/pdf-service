# CNSE Convert -- Universal File-to-PDF Converter (GCP Cloud Run)

CNSE Convert ist ein skalierbarer, serverloser Microservice zum
Konvertieren verschiedenster Dateitypen in PDF -- betrieben auf **Google
Cloud Run** und entwickelt mit **Java/Spring Boot**.\
Der Service akzeptiert u. a. **Bilder**, **ZIP-/RAR-Archive**,
**Dokumente**, **Binärdateien** und erzeugt daraus ein oder mehrere
PDF-Dokumente.

------------------------------------------------------------------------

## 🚀 Zusammenfassung

CNSE Convert ermöglicht das automatische, standardisierte Umwandeln
beliebiger Dateien in PDF.\
Ideal für Upload-Portale, Dokumentenverarbeitung,
Automatisierungsprozesse und Backoffice-Systeme.

------------------------------------------------------------------------

## ✨ Features

-   🖼️ **Bildkonvertierung** (PNG, JPG, GIF, WEBP → PDF)\
-   📦 **Archiv-Support** (ZIP/RAR → Inhalte extrahieren → Sammel-PDF)\
-   📄 **Generische Binärdateien** werden analysiert (z. B. Hexdump) und
    als PDF dargestellt\
-   📚 **Mehrseitige PDFs** bei mehreren Dateien\
-   ☁️ **Cloud Run** -- voll autoskalierend\
-   🧹 **Keine Speicherung** von Dateien (Memory-only Verarbeitung)\
-   🔒 **HTTPS** by default\
-   ⚡ **Sehr schnelle Konvertierung** dank schlanker Architektur

------------------------------------------------------------------------

## 🏗️ Architekturüberblick

Client → Cloud Run API → CNSE Convert (Spring Boot) → PDF Engine →
Response (PDF)

------------------------------------------------------------------------

## 📥 API-Endpunkte

### `POST /convert`

Konvertiert eine Datei in ein PDF-Dokument.

**Beispiel:**

``` bash
curl -X POST "https://<your-cloud-run-url>/convert"   -F "file=@/path/to/your/file.png"   --output output.pdf
```

**Response:**\
- `Content-Type: application/pdf`\
- PDF-Datei als Binärstream

------------------------------------------------------------------------

## 💻 Lokale Nutzung

### Voraussetzungen

-   Java 17+
-   Maven
-   Docker (optional)

### Starten (lokal)

``` bash
mvn spring-boot:run
```

### Build (JAR erzeugen)

``` bash
mvn clean package
```

------------------------------------------------------------------------

## ☁️ Deployment auf Google Cloud Run

``` bash
gcloud builds submit --tag gcr.io/<PROJECT-ID>/cnse-convert
gcloud run deploy cnse-convert   --image gcr.io/<PROJECT-ID>/cnse-convert   --platform managed   --region europe-west3   --allow-unauthenticated
```

------------------------------------------------------------------------

## 🖼️ Screenshots

(Platzhalter -- bitte mit realen Screenshots ersetzen)

-   `docs/screenshots/ui-overview.png`
-   `docs/screenshots/output-example.png`

------------------------------------------------------------------------

## 📁 Projektstruktur

    /src
      /main/java/.../controller        → REST API
      /main/java/.../service           → Konvertierungslogik
      /main/java/.../utils             → Parser & Hilfsklassen
      /main/resources                  → Konfiguration, Templates
    /docs/screenshots                  → Screenshots für README
    Dockerfile
    README.md

------------------------------------------------------------------------

## 🛡️ Sicherheit & Datenschutz

-   Keine Dateispeicherung -- Verarbeitung findet ausschließlich im
    Arbeitsspeicher statt\
-   Ausschließlich HTTPS-Zugriff über Cloud Run\
-   Logs enthalten niemals Datei-Inhalte

------------------------------------------------------------------------

## 📜 Lizenz

MIT License

------------------------------------------------------------------------

## 📞 Kontakt

Bei Fragen oder Erweiterungswünschen:\
**CNSE Development** -- christian.seelert@example.com

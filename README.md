# CNSE Convert -- Image-to-PDF Converter (GCP Cloud Run)


<a href="https://pdf-api-689516465881.europe-west3.run.app/">CNSE Convert</a> ist ein skalierbarer, serverloser Microservice zum
Konvertieren verschiedenster Bilddateitypen in PDF -- betrieben auf **Google
Cloud Run** und entwickelt mit **Java/Spring Boot**.\
Der Service akzeptiert u. a. **PNG**, **JPG**, und erzeugt daraus ein
PDF-Dokument.

------------------------------------------------------------------------

## 🚀 Zusammenfassung

CNSE Convert ermöglicht das automatische, standardisierte Umwandeln
beliebiger Bilder in PDF.\
Ideal für Upload-Portale, Dokumentenverarbeitung,
Automatisierungsprozesse und Backoffice-Systeme.

------------------------------------------------------------------------

## ✨ Features

-   🖼️ **Bildkonvertierung** (PNG, JPG, GIF, WEBP → PDF)
-   📚 **Mehrseitige PDFs** bei mehreren Dateien
-   ☁️ **Cloud Run** -- voll autoskalierend
-   🔒 **HTTPS** by default
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
curl -X POST "https://pdf-api-689516465881.europe-west3.run.app/convert"   -F "file=@/path/to/your/file.png"   --output output.pdf
```

**Response:**\
- `Content-Type: application/pdf`\
- PDF-Datei als Binärstream

------------------------------------------------------------------------

## ☁️ Deployment auf Google Cloud Run

Automatisch bei Commits via GitHub Actions Workflow

------------------------------------------------------------------------

## 🖼️ Screenshots
**Seite**
<img width="1380" height="360" alt="image" src="https://github.com/sirr0n89/pdf-service/blob/main/docs/Index.png" />

**Converted File**
<img width="1380" height="360" alt="image" src="https://github.com/sirr0n89/pdf-service/blob/main/docs/Converted.png" />

**Dienste**
<img width="1380" height="360" alt="image" src="https://github.com/sirr0n89/pdf-service/blob/main/docs/Dienste.png" />

**Buckets**
<img width="1380" height="360" alt="image" src="https://github.com/sirr0n89/pdf-service/blob/main/docs/Buckets.png" />

**PubSub**
<img width="1380" height="360" alt="image" src="https://github.com/sirr0n89/pdf-service/blob/main/docs/PubSub.png" />


------------------------------------------------------------------------

## 📁 Projektstruktur

    /src/main/java/...
        /config                 → GCP- & Spring-Konfiguration
        /convert                → Konvertierungslogik (ImageToPdfService)
        /jobs                   → Pub/Sub-Jobs & Worker
        /storage                → Zugriff auf GCS & File-Metadaten
        ConvertController       → REST-Endpoint für /convert
        HealthController        → Health-Check-Endpoint
        PdfserviceApplication   → Spring-Boot-Mainklasse

------------------------------------------------------------------------

## 🛡️ Sicherheit & Datenschutz

-   Ausschließlich HTTPS-Zugriff über Cloud Run\
-   Logs enthalten niemals Datei-Inhalte

------------------------------------------------------------------------

## 📜 Lizenz

MIT License

------------------------------------------------------------------------

## 📞 Kontakt

Bei Fragen oder Erweiterungswünschen:\
**CNSE Development** -- chse1001@stud.hs-kl.de

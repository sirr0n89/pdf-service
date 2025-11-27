const dropZone = document.getElementById('dropZone');
const fileInput = document.getElementById('fileInput');
const browseLink = document.getElementById('browseLink');
const uploadBtn = document.getElementById('uploadBtn');
const fileInfo = document.getElementById('fileInfo');
const fileNameSpan = document.getElementById('fileName');
const progressWrapper = document.getElementById('progressWrapper');
const progressFill = document.getElementById('progressFill');
const progressPercent = document.getElementById('progressPercent');
const statusText = document.getElementById('statusText');
const errorBox = document.getElementById('errorBox');
const errorMessage = document.getElementById('errorMessage');

let selectedFile = null;

function resetProgress() {
    progressFill.style.width = '0%';
    progressPercent.textContent = '0%';
}

function setFile(file) {
    selectedFile = file;
    if (file) {
        fileNameSpan.textContent =
            file.name + ' (' + Math.round(file.size / 1024) + ' KB)';
        fileInfo.classList.remove('hidden');
        uploadBtn.disabled = false;
    } else {
        fileInfo.classList.add('hidden');
        uploadBtn.disabled = true;
    }
}

// Browse-Link öffnet Dateidialog
browseLink.addEventListener('click', function (e) {
    e.preventDefault();
    fileInput.click();
});

fileInput.addEventListener('change', function () {
    if (fileInput.files && fileInput.files.length > 0) {
        setFile(fileInput.files[0]);
    } else {
        setFile(null);
    }
});

// Drag & Drop Events
['dragenter', 'dragover'].forEach(eventName => {
    dropZone.addEventListener(eventName, (e) => {
        e.preventDefault();
        e.stopPropagation();
        dropZone.classList.add('drag-over');
    });
});

['dragleave', 'drop'].forEach(eventName => {
    dropZone.addEventListener(eventName, (e) => {
        e.preventDefault();
        e.stopPropagation();
        dropZone.classList.remove('drag-over');
    });
});

dropZone.addEventListener('drop', (e) => {
    const dt = e.dataTransfer;
    const files = dt.files;
    if (files && files.length > 0) {
        setFile(files[0]); // erste Datei verwenden
    }
});

// Upload mit XHR + Progress
document.getElementById('uploadForm').addEventListener('submit', function (e) {
    e.preventDefault();
    errorBox.classList.add('hidden');
    statusText.classList.add('hidden');

    if (!selectedFile) {
        return;
    }

    uploadBtn.disabled = true;
    progressWrapper.classList.remove('hidden');
    resetProgress();
    statusText.textContent = 'Upload läuft…';
    statusText.classList.remove('hidden');

    const formData = new FormData();
    formData.append('file', selectedFile);

    const xhr = new XMLHttpRequest();
    xhr.open('POST', '/convert', true);

    xhr.upload.addEventListener('progress', function (event) {
        if (event.lengthComputable) {
            const percent = Math.round((event.loaded / event.total) * 100);
            progressFill.style.width = percent + '%';
            progressPercent.textContent = percent + '%';
        } else {
            // wenn keine Größe bekannt ist, lass einfach eine volle Bar laufen
            progressFill.style.width = '100%';
        }
    });

    xhr.onreadystatechange = function () {
        if (xhr.readyState === XMLHttpRequest.DONE) {
            if (xhr.status >= 200 && xhr.status < 400) {
                statusText.textContent = 'Upload abgeschlossen. Weiterleiten…';
                const targetUrl = xhr.responseURL || '/';
                window.location.href = targetUrl; // /job/{jobId}
            } else {
                uploadBtn.disabled = false;
                statusText.classList.add('hidden');
                errorMessage.textContent =
                    'Upload fehlgeschlagen (Status ' + xhr.status + ').';
                errorBox.classList.remove('hidden');
            }
        }
    };

    xhr.send(formData);
});

// Optional: Einfügen von Dateien mit Paste
window.addEventListener('paste', (e) => {
    const items = e.clipboardData && e.clipboardData.items;
    if (!items) return;
    for (let i = 0; i < items.length; i++) {
        const item = items[i];
        if (item.kind === 'file') {
            const file = item.getAsFile();
            if (file) {
                setFile(file);
                break;
            }
        }
    }
});

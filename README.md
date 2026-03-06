# 🎤 Speech Diarization with Azure Cognitive Services

<img width="1376" height="768" alt="Binod_Suman_Azure_Trnscription_Translation" src="https://github.com/user-attachments/assets/66175c7e-e194-46f3-a976-5d8bcfe3be78" />


A Spring Boot application that performs speaker diarization on WAV files using Azure Speech Services. The application identifies different speakers in an audio file, provides timestamped transcripts with speaker labels, and **automatically translates non-English content to English** while preserving the original language.

## 📋 Table of Contents
- [Azure Setup Guide](#azure-setup-guide)
- [Quick Start](#quick-start)
- [Features](#features)
- [Configuration](#configuration)
    - [Azure Configuration Properties](#azure-configuration-properties)
    - [Application Properties](#application-properties)
- [Language Support & Translation](#language-support--translation)
- [Java Classes Overview](#java-classes-overview)
- [Detailed Class Descriptions](#detailed-class-descriptions)
- [API Endpoints](#api-endpoints)
- [REST API](#rest-api)
- [Usage Guide](#usage-guide)
- [File Naming Convention](#file-naming-convention)
- [Troubleshooting](#troubleshooting)
- [Security Features](#security-features)
- [Performance Considerations](#performance-considerations)
- [Monitoring](#monitoring)

---

## ☁️ Azure Setup Guide

### Step 1: Create Speech Service

1. Go to [Azure Portal](https://portal.azure.com)
2. In top search bar, type "Speech Services"
3. Click "Create" under Speech Services
4. Fill in the form:
    - **Subscription**: Select your Azure subscription
    - **Resource group**: Create new or select existing
    - **Region**: Choose nearest (e.g., East US)
    - **Name**: Your service name (e.g., "my-speech-service")
    - **Pricing tier**: Free F0 (for testing) or Standard S0
5. Click "Review + create"
6. Click "Create"
7. Wait for deployment (1-2 minutes)
8. Click "Go to resource"

### Step 2: Get Speech Credentials

1. In your Speech Service resource, click "Keys and Endpoint" in left menu
2. Copy either "KEY 1" or "KEY 2" - this is your `azure.speech.key`
3. Note the "Location/Region" (e.g., "eastus") - this is your `azure.speech.region`

### Step 3: Create Translator Service

1. In Azure Portal, search "Translator"
2. Click "Create"
3. Fill in the form:
    - **Subscription**: Your subscription
    - **Resource group**: Use same as Speech Service
    - **Region**: Same as Speech Service (e.g., eastus)
    - **Name**: Your service name (e.g., "my-translator")
    - **Pricing tier**: Free F0 (for testing) or Standard S1
4. Click "Review + create"
5. Click "Create"
6. Wait for deployment
7. Click "Go to resource"

### Step 4: Get Translator Credentials

1. In your Translator resource, click "Keys and Endpoint" in left menu
2. Copy either "KEY 1" or "KEY 2" - this is your `azure.translator.key`
3. Note the "Location/Region" - this is your `azure.translator.region`
4. Copy the "Endpoint" (e.g., `https://api.cognitive.microsofttranslator.com/`) - this is your `azure.translator.endpoint`

### Step 5: Create Storage Account

1. In Azure Portal, search "Storage accounts"
2. Click "Create"
3. Fill in basics:
    - **Subscription**: Your subscription
    - **Resource group**: Use same as Speech Service
    - **Storage account name**: Globally unique name (e.g., "mydiarizationstorage")
    - **Region**: Same as Speech Service
    - **Performance**: Standard
    - **Redundancy**: Locally-redundant storage (LRS)
4. Click "Review + create"
5. Click "Create"
6. Wait for deployment
7. Click "Go to resource"

### Step 6: Get Storage Credentials

1. In your Storage Account, click "Access keys" in left menu under "Security + networking"
2. Click "Show" next to any key
3. Copy the "Connection string" - this is your `azure.storage.connection-string`
    - Format: `DefaultEndpointsProtocol=https;AccountName=...;AccountKey=...;EndpointSuffix=core.windows.net`

### Step 7: Create Container

1. In your Storage Account, click "Containers" in left menu under "Data storage"
2. Click "+ Container"
3. Name: `speech-files` (or your preferred name)
4. Public access level: "Private (no anonymous access)"
5. Click "Create"

### Step 8: Configure Application

Update `src/main/resources/application.properties` with your values:
```properties
# Speech Service
azure.speech.key=your-speech-key-here
azure.speech.region=eastus

# Translator Service
azure.translator.key=your-translator-key-here
azure.translator.region=eastus
azure.translator.endpoint=https://api.cognitive.microsofttranslator.com/

# Storage
azure.storage.connection-string=your-connection-string-here
azure.storage.container-name=speech-files





### Azure Costs Estimation

| Service | Free Tier | Standard Tier |
|---------|-----------|---------------|
| Speech Service | 5 audio hours/month | $1.00 per audio hour |
| Translator | 2M characters/month | $10 per 1M characters |
| Storage Account | 5 GB free | $0.02 per GB/month |
| Bandwidth | 5 GB outbound free | $0.087 per GB |

---

## 🚀 Quick Start

### Clone and Build

```bash
# Clone the repository
git clone https://github.com/your-repo/speech-diarization-app.git
cd speech-diarization-app

# Configure Azure credentials
# Edit src/main/resources/application.properties with your Azure keys

# Build the application
mvn clean install

# Run the application
mvn spring-boot:run
```

### Access the Application

Once running, open your browser and navigate to:
```
http://localhost:8080
```

---

## ✨ Features

- **Speaker Diarization**: Identifies different speakers in audio files with color-coded segments
- **Multi-language Support**: Auto-detects language from audio content
- **Automatic Translation**: Translates non-English transcripts to English using Azure Translator
- **Bilingual Display**: Toggle between original language and English translation in UI
- **Native Script Preservation**: Maintains original scripts (Devanagari for Hindi, etc.)
- **File Persistence**: Stores original files and transcripts in Azure Blob Storage with original filenames
- **Duplicate Detection**: Checks if a file was already processed and returns existing transcript
- **Asynchronous Processing**: Handles long-running transcription jobs in the background
- **Real-time Status**: Live progress tracking with auto-refreshing UI
- **Email Notifications**: Optional email alerts when transcription completes
- **Detailed Logging**: Comprehensive logs for debugging and monitoring
- **Job History**: View status of all transcription jobs
- **REST API**: Full API support for integration with other applications

---

## ⚙️ Configuration

### Azure Configuration Properties

| Property | Description | Where to Find in Azure Portal |
|----------|-------------|-------------------------------|
| `azure.speech.key` | Azure Speech API key | Speech Service → Keys and Endpoint → KEY 1 |
| `azure.speech.region` | Azure region (e.g., eastus) | Speech Service → Overview → Location |
| `azure.speech.language` | Language code for transcription | Default: auto (auto-detect) |
| `azure.translator.key` | Azure Translator API key | Translator Service → Keys and Endpoint → KEY 1 |
| `azure.translator.region` | Translator region | Translator Service → Overview → Location |
| `azure.translator.endpoint` | Translator endpoint | Translator Service → Keys and Endpoint → Endpoint |
| `azure.storage.connection-string` | Blob storage connection string | Storage Account → Access keys → Connection string |
| `azure.storage.container-name` | Container name for storing files | Storage Account → Containers → Your container name |

### Application Properties

| Property | Description | Default Value | Required |
|----------|-------------|---------------|----------|
| `server.port` | Server port | 8080 | No |
| `app.base-url` | Base URL for email links | http://localhost:8080 | No |
| `spring.servlet.multipart.max-file-size` | Maximum file upload size | 200MB | No |
| `spring.servlet.multipart.max-request-size` | Maximum request size | 200MB | No |
| `spring.thymeleaf.cache` | Thymeleaf template caching | false | No |
| `job.polling.max-attempts` | Max polling attempts for Azure job | 120 | No |
| `job.status.update-interval` | Polling interval in seconds | 5 | No |
| `transcription.keep-original-language` | Keep original transcript alongside English | true | No |
| `transcription.target-language` | Target language for translation | en | No |
| `transcription.auto-detect` | Auto-detect language from audio | true | No |
| `spring.mail.enabled` | Enable email notifications | false | No |
| `spring.mail.host` | SMTP host | smtp.gmail.com | If email enabled |
| `spring.mail.port` | SMTP port | 587 | If email enabled |
| `spring.mail.username` | Email username | - | If email enabled |
| `spring.mail.password` | Email password | - | If email enabled |
| `logging.level.com.example.speechapp` | Application log level | DEBUG | No |
| `logging.file.name` | Log file location | logs/speech-diarization.log | No |

---

## 🌐 Language Support & Translation

The application supports automatic language detection and translation for multiple languages:

### Supported Languages

| Language | Code | Script | Translation Support |
|----------|------|--------|---------------------|
| English | `en` | Latin | Yes (source/target) |
| Spanish | `es` | Latin | Yes |
| French | `fr` | Latin | Yes |
| German | `de` | Latin | Yes |
| Italian | `it` | Latin | Yes |
| Portuguese | `pt` | Latin | Yes |
| Russian | `ru` | Cyrillic | Yes |
| Hindi | `hi` | Devanagari | Yes |
| Chinese | `zh` | Han | Yes |
| Japanese | `ja` | Kanji | Yes |
| Korean | `ko` | Hangul | Yes |
| Arabic | `ar` | Arabic | Yes |

### How Translation Works

1. **Language Detection**: Azure Speech auto-detects the language from audio
2. **Transcription**: Speech-to-text in the detected language
3. **Translation Check**: If detected language is not English, translation is triggered
4. **Bilingual Storage**: Both original and translated versions are saved with language suffixes
5. **UI Display**: Toggle between original and English views

### File Naming Convention with Language Support

```
Original audio: meeting.wav
├── Original transcript: meeting_hi_transcript.json (Hindi)
└── English translation: meeting_en_transcript.json (English)
```

---

## 📚 Java Classes Overview

| Class | Package | Purpose | Key Methods |
|-------|---------|---------|-------------|
| **SpeechDiarizationApplication** | `com.example.speechapp` | Main Spring Boot application class with async support | `main()` |
| **AzureSpeechConfig** | `config` | Speech service configuration holder | Getters/setters for key, region, language |
| **AzureBlobConfig** | `config` | Blob storage configuration holder | Getters/setters for connection string, container |
| **TranslatorConfig** | `config` | Translator service configuration | Getters/setters for key, region, endpoint |
| **SpeechController** | `controller` | REST endpoints and UI routing | `upload()`, `getJobStatus()`, `listJobs()`, `checkFile()` |
| **SpeechService** | `service` | Core transcription logic with async processing | `startTranscriptionJob()`, `processJobAsync()`, `pollForTranscriptionResult()` |
| **TranslationService** | `service` | Azure Translator integration | `translateText()`, `detectLanguage()`, `isEnglish()` |
| **BlobStorageService** | `service` | Azure Blob operations with file checking | `uploadFileAndGenerateSas()`, `fileExists()`, `transcriptExists()`, `saveTranscriptionResult()` |
| **EmailService** | `service` | Email notification service | `sendCompletionNotification()`, `sendFailureNotification()` |
| **TranscriptionJob** | `model` | Job state management with language fields | Status tracking, progress updates, formatted file size |
| **TranscriptionResult** | `model` | Azure API response mapping with nested classes | Parses JSON response from Azure |

---

## 📖 Detailed Class Descriptions

### 📁 config Package

**AzureSpeechConfig.java**
- **Purpose**: Configuration properties holder for Azure Speech Service
- **Properties**:
    - `key`: Azure Speech API key
    - `region`: Azure region (eastus, westus2, etc.)
    - `language`: Language code for transcription (default: en-US)
- **Usage**: Auto-wired into SpeechService for making API calls to Azure Speech

**AzureBlobConfig.java**
- **Purpose**: Configuration properties holder for Azure Blob Storage
- **Properties**:
    - `connectionString`: Full Azure Storage connection string
    - `containerName`: Blob container name for storing files
- **Usage**: Auto-wired into BlobStorageService for all storage operations
- **Note**: Container is automatically created if it doesn't exist

**TranslatorConfig.java**
- **Purpose**: Configuration for Azure Translator Service
- **Properties**:
    - `key`: Azure Translator API key
    - `region`: Translator region
    - `endpoint`: Translator endpoint URL
- **Usage**: Auto-wired into TranslationService

### 📁 controller Package

**SpeechController.java**
- **Purpose**: Handles all HTTP requests and routes them to appropriate services
- **Key Methods**:
    - `index()`: Serves the upload page with file dropzone
    - `handleFileUpload()`: Processes file upload, checks for existing transcripts, starts jobs
    - `getJobStatus()`: Displays job status page with auto-refresh and language toggle
    - `getJobStatusApi()`: REST endpoint for job status JSON with bilingual segments
    - `listJobs()`: Shows recent transcription jobs
    - `checkFileExistence()`: API endpoint to check if file already processed
- **Features**: File validation, error handling, redirect logic, language detection

### 📁 service Package

**SpeechService.java**
- **Purpose**: Core business logic for speech transcription
- **Key Methods**:
    - `startTranscriptionJob()`: Creates new job and returns immediately
    - `processJobAsync()`: Async method that handles the entire transcription pipeline
    - `createTranscription()`: Calls Azure API to create transcription job
    - `pollForTranscriptionResult()`: Polls Azure for job completion
    - `parseTranscriptionResult()`: Converts Azure JSON to speaker segments
- **Features**: Async processing, progress tracking, detailed logging, error handling, language detection, translation coordination
- **Inner Class**: `SpeakerSegment` - Represents each spoken segment with speaker ID, text, translated text, timestamps, and confidence

**TranslationService.java**
- **Purpose**: Handles all translation operations
- **Key Methods**:
    - `translateText()`: Translates text to target language
    - `detectLanguage()`: Detects language from text sample
    - `isEnglish()`: Checks if language is English
    - `getBaseLanguageCode()`: Extracts base language from locale
- **Features**: REST API integration with Azure Translator, error handling, rate limiting

**BlobStorageService.java**
- **Purpose**: All Azure Blob Storage operations
- **Key Methods**:
    - `fileExists()`: Checks if audio file exists in blob storage
    - `transcriptExists()`: Checks if transcript exists for a file
    - `getTranscriptContent()`: Retrieves existing transcript JSON
    - `uploadFileAndGenerateSas()`: Uploads file and returns SAS URL
    - `saveTranscriptionResult()`: Saves transcript with language suffix
    - `getProcessedFiles()`: Lists all processed files with metadata
- **Features**: SAS URL generation, automatic container creation, filename sanitization, language-aware file naming

**EmailService.java**
- **Purpose**: Handles email notifications
- **Key Methods**:
    - `sendCompletionNotification()`: Sends success email with job details including language info
    - `sendFailureNotification()`: Sends failure email with error message
- **Features**: Conditional sending, formatted email bodies, error handling

### 📁 model Package

**TranscriptionJob.java**
- **Purpose**: Tracks state of each transcription job with enhanced language tracking
- **States**:
    - `UPLOADING`: File being uploaded to blob storage
    - `PROCESSING`: Azure processing the audio
    - `COMPLETED`: Transcription complete with results
    - `FAILED`: Job failed with error message
- **Fields**:
    - `jobId`: Unique identifier
    - `fileName`: Original filename
    - `fileSize`: Size in bytes
    - `status`: Current job state
    - `progress`: Progress percentage (0-100)
    - `detectedLanguage`: Language detected from audio
    - `translated`: Whether translation was performed
    - `isEnglishAudio`: Flag for English content
    - `segments`: List of speaker segments
    - `originalTranscriptUrl`: URL of original language transcript
    - `translatedTranscriptUrl`: URL of English translation
- **Methods**: `getFormattedFileSize()`, `getLanguageDisplayName()`, status management

**TranscriptionResult.java**
- **Purpose**: Maps Azure Speech API JSON response to Java objects
- **Nested Classes**:
    - `CombinedPhrase`: Combined transcription for all speakers
    - `RecognizedPhrase`: Individual phrase with speaker ID
    - `NBest`: Best recognition result with confidence
    - `DisplayWord`: Word-level timestamps
- **Features**: Comprehensive JSON mapping with @JsonIgnoreProperties

**SpeakerSegment.java** (Inner class in SpeechService)
- **Purpose**: Represents a single speaker segment with bilingual support
- **Fields**:
    - `speakerId`: Speaker identifier (e.g., "Speaker 1")
    - `text`: Original language text in native script
    - `translatedText`: English translation (for non-English audio)
    - `offset`: Start time in milliseconds
    - `duration`: Segment duration in milliseconds
    - `confidence`: Recognition confidence score (0.0 to 1.0)
- **Methods**:
    - `hasTranslation()`: Checks if segment has English translation
    - `getFormattedTime()`: Returns formatted timestamp (MM:SS.mmm)

---

## 🌐 API Endpoints

### Web UI Endpoints

| Method | URL | Description | Parameters |
|--------|-----|-------------|------------|
| GET | `/` | Upload page with file dropzone and language options | None |
| GET | `/status/{jobId}` | Real-time job status page with language toggle | `jobId`: Job identifier |
| GET | `/jobs` | List recent transcription jobs with language info | None |

### REST API Endpoints

| Method | URL | Description | Request Body | Response |
|--------|-----|-------------|--------------|----------|
| POST | `/upload` | Upload file and start job | `file`: WAV file<br>`email`: (optional) | Redirect to `/status/{jobId}` |
| GET | `/api/status/{jobId}` | Get job status as JSON with bilingual segments | None | Job details with original and translated text |
| POST | `/api/check-file` | Check if file already processed | `file`: WAV file | `{"exists": true/false, "languages": []}` |
| GET | `/api/transcript/{filename}` | Get transcript JSON | None | Full transcript JSON with language metadata |
| GET | `/api/jobs` | List all jobs | None | Array of job objects with language info |

---

## 📡 REST API

### Check File Existence
```bash
curl -X POST -F "file=@audio.wav" http://localhost:8080/api/check-file
```
Response:
```json
{
  "exists": true,
  "filename": "audio.wav",
  "availableTranscripts": [
    {"language": "hi", "url": "/api/transcript/audio_hi_transcript.json"},
    {"language": "en", "url": "/api/transcript/audio_en_transcript.json"}
  ]
}
```

### Get Job Status with Language Info
```bash
curl http://localhost:8080/api/status/job-123
```
Response:
```json
{
  "jobId": "job-123",
  "status": "COMPLETED",
  "progress": 100,
  "message": "Transcription completed",
  "fileName": "meeting.wav",
  "detectedLanguage": "hi",
  "isEnglishAudio": false,
  "translated": true,
  "totalSpeakers": 3,
  "totalDuration": 125000,
  "segments": [
    {
      "speakerId": "Speaker 1",
      "text": "नमस्ते, मैं अपने ऑर्डर के बारे में बात करना चाहता हूँ।",
      "translatedText": "Hello, I want to talk about my order.",
      "offset": 0,
      "duration": 2500,
      "confidence": 0.95,
      "formattedTime": "00:00.000"
    }
  ]
}
```

### Get Transcript JSON
```bash
curl http://localhost:8080/api/transcript/meeting_hi_transcript.json
```
Returns the full Azure Speech JSON response with language metadata.

### List All Jobs
```bash
curl http://localhost:8080/api/jobs
```
Returns array of recent jobs with status and language information.

---

## 📖 Usage Guide

### 1. Upload Page

The upload page (`http://localhost:8080`) features:

- **File Dropzone**: Drag & drop or click to select a WAV file
- **File Validation**: Automatically checks file type and size
- **Email Field**: Optional email for notifications
- **Language Badge**: Shows detected language of previously processed files
- **Recent Files List**: Displays previously processed files with their languages

### 2. Uploading a File

1. Navigate to `http://localhost:8080`
2. Drag & drop a WAV file onto the dropzone or click to select
3. (Optional) Enter email address for notification when complete
4. Click "Start Transcription" button
5. You'll be automatically redirected to the status page

### 3. File Existence Check

When you select a file, the application automatically checks if it was previously processed:

- **If file is new**: Normal processing begins immediately
- **If file exists with transcript**:
    - Warning banner appears at the top
    - Shows available language versions (e.g., Hindi, English)
    - "View Existing Transcript" button appears
    - Option to upload anyway (will overwrite)
    - Submit button is disabled until you choose

### 4. Status Page

The status page (`/status/{jobId}`) provides real-time updates:

**During Processing:**
- **Progress Bar**: Visual indication of completion (0-100%)
- **Status Message**: Current step (Uploading, Processing, Parsing, Translating)
- **Job Details**: File name, size, creation time
- **Auto-refresh**: Page refreshes every 5 seconds
- **Logs**: Real-time progress in server console

**Processing States:**
| State | Progress | Description |
|-------|----------|-------------|
| **UPLOADING** | 10% | File being uploaded to Azure Blob Storage |
| **PROCESSING** | 20-60% | Azure Speech processing audio with diarization |
| **TRANSLATING** | 75% | Translating non-English content (if needed) |
| **COMPLETED** | 100% | Transcription complete with results |

### 5. Viewing Results

When transcription completes, the results page shows:

**Summary Statistics:**
- Total number of segments
- Number of unique speakers detected
- Total duration of speech
- Detected language badge

**For English Audio:**
- Single language view with English text
- Confidence percentage for each segment
- Timestamps and duration

**For Non-English Audio (e.g., Hindi, Spanish):**
- **Language Toggle** appears above results
- Click "Original" to see text in native script (e.g., Devanagari for Hindi)
- Click "English" to see translated version
- Both versions are displayed with proper formatting

### 6. Speaker Segments Display

Each speaker segment shows:

| Element | Description |
|---------|-------------|
| **Speaker ID** | Color-coded by speaker (Speaker 1-5) |
| **Original Text** | In detected language with proper script |
| **Translated Text** | English translation (for non-English audio) |
| **Confidence Badge** | Recognition confidence percentage |
| **Timestamp** | Start time and duration |

### 7. Job History Page

Navigate to `/jobs` to see:

- List of all recent jobs with their current status
- Detected language for each job
- Progress percentage
- Color-coded status badges
- Quick links to job details

**Status Badges:**
| Status | Color | Description |
|--------|-------|-------------|
| **UPLOADING** | Yellow | File being uploaded |
| **PROCESSING** | Blue | Azure processing audio |
| **COMPLETED** | Green | Transcription done |
| **FAILED** | Red | Job failed |

### 8. Email Notifications

If email is configured and provided:

- **On Success**: Email with job details, language info, and link to results
- **On Failure**: Email with error message for troubleshooting
- Links point to the status page for full bilingual results

### 9. Accessing Raw JSON

For integration or debugging:

- Click "View Transcript" link on job details page
- Direct API access via `/api/transcript/{filename}`
- Raw Azure Speech JSON format preserved with language metadata
- Includes both original and translated text

---

## 📝 File Naming Convention with Languages

### Audio Files
- Original filename is preserved exactly as uploaded
- Example: `team-meeting-2024-01-15.wav`
- Special characters replaced with underscores for safety

### Transcript Files
| Type | Format | Example |
|------|--------|---------|
| Original Language | `[name]_[code]_transcript.json` | `team-meeting_hi_transcript.json` |
| English Translation | `[name]_en_transcript.json` | `team-meeting_en_transcript.json` |

### Example File Structure in Blob
```
speech-files/
├── meeting.wav
├── meeting_hi_transcript.json
├── meeting_en_transcript.json
├── interview.wav
├── interview_es_transcript.json
├── interview_en_transcript.json
└── conference_call.wav
    ├── conference_call_fr_transcript.json
    └── conference_call_en_transcript.json
```

---

## 🔍 Troubleshooting

### Common Issues and Solutions

| Issue | Symptom | Solution |
|-------|---------|----------|
| **Connection refused** | "Failed to create transcription" | Check Azure credentials in properties |
| **File too large** | Upload fails | Max file size is 200MB (configurable) |
| **Wrong file type** | "Please upload a WAV file" | Only WAV format supported |
| **Transcription timeout** | Job stuck in PROCESSING | Increase `job.polling.max-attempts` |
| **Blob storage errors** | "Error uploading to blob" | Verify connection string and container permissions |
| **No speech detected** | Empty results | Check audio quality and content |
| **Email not sending** | No notifications | Verify SMTP settings and enable spring.mail.enabled |
| **Translation fails** | "Translation error" in logs | Check Translator key and region |
| **Wrong language detected** | Incorrect language badge | Verify audio quality and content |
| **Hindi shows as English** | Hindi text transliterated | Check UI language toggle setting |
| **Translation quota exceeded** | 429 error in logs | Upgrade Translator tier |

### Logs

Logs are written to two locations:

**Console Output** (real-time):
```
2024-01-01 12:00:01 - ================================================================================
2024-01-01 12:00:01 - PROCESSING FILE: meeting.wav
2024-01-01 12:00:01 - ================================================================================
2024-01-01 12:00:02 - [10%] Uploading to Azure Blob Storage...
2024-01-01 12:00:03 - ✓ File uploaded successfully
2024-01-01 12:00:03 - [25%] Creating Azure Speech transcription job...
2024-01-01 12:00:04 - ✓ Transcription job created
2024-01-01 12:00:05 - 🔍 Detected language: hi
2024-01-01 12:00:06 - 🔄 Translating from hi to en
2024-01-01 12:00:07 - ✓ Translation completed for segment 1/6
```

**Log File** (`logs/speech-diarization.log`):
```
2024-01-01 12:00:01 [main] INFO  com.example.speechapp.service.SpeechService - ================================================================================
2024-01-01 12:00:01 [main] INFO  com.example.speechapp.service.SpeechService - PROCESSING FILE: meeting.wav
2024-01-01 12:00:01 [main] INFO  com.example.speechapp.service.SpeechService - ================================================================================
```

### Debug Mode

Enable debug logging for more details:
```properties
logging.level.com.example.speechapp=DEBUG
logging.level.com.example.speechapp.service.TranslationService=DEBUG
```

View logs in real-time:
```bash
tail -f logs/speech-diarization.log
```

---

## 🔒 Security Features

### Authentication
- All Azure API calls use key-based authentication
- Keys stored in properties file (not in code)
- Support for Azure Key Vault (optional)

### File Access
- **SAS URLs**: Temporary access URLs with 1-hour expiry
- **Read-only**: SAS tokens only allow read operations
- **No public access**: Blob container is private
- **Auto-cleanup**: Temporary files deleted after processing

### Data Protection
- **In transit**: All HTTPS connections
- **At rest**: Azure Storage encryption
- **No local storage**: Files processed in memory, streamed to Azure

### Best Practices
1. Rotate API keys regularly
2. Use different keys for development/production
3. Enable Azure Defender for additional security
4. Monitor access logs in Azure Portal

---

## 🚦 Performance Considerations

### File Size Limits
- **Maximum**: 200MB (configurable)
- **Recommended**: < 100MB for optimal performance
- **Duration**: Up to 2 hours of audio

### Processing Times

| Audio Duration | Transcription | Translation | Total |
|----------------|---------------|-------------|-------|
| 5 minutes | 30-60 seconds | 2-3 seconds | ~1 minute |
| 30 minutes | 2-3 minutes | 10-15 seconds | ~3 minutes |
| 1 hour | 4-5 minutes | 20-30 seconds | ~5 minutes |
| 2 hours | 8-10 minutes | 40-60 seconds | ~10 minutes |

### Concurrent Jobs

| Azure Tier | Concurrent Transcriptions |
|------------|--------------------------|
| Free (F0) | 1 job at a time |
| Standard (S0) | 20 jobs at a time |

### Translation Limits

| Tier | Characters per month | Concurrent requests |
|------|---------------------|---------------------|
| Free F0 | 2 million | 1 |
| Standard S1 | Unlimited | 20 |

### Memory Usage
- Heap: ~256MB for application
- Per job: Additional ~50MB during processing
- Recommended: 1GB heap for production

---

## 📊 Monitoring

### Application Health Checks
- Endpoint: `/actuator/health` (if Spring Actuator enabled)
- Returns: UP/DOWN status with component details

### Key Metrics to Monitor

| Metric | Description | Alert Threshold |
|--------|-------------|-----------------|
| Job Success Rate | % of completed vs failed jobs | < 90% |
| Average Processing Time | Per audio minute | > 2 min per min of audio |
| Queue Length | Number of pending jobs | > 10 |
| Error Rate | Frequency of failures | > 5% |
| Language Detection Accuracy | Mismatch rate | > 5% |
| Translation Success Rate | Failed translations | > 2% |
| Characters Translated | Monthly usage | > 80% of quota |

### Azure Portal Monitoring

**Speech Service Metrics**:
- Successful requests
- Failed requests
- Audio hours processed
- Latency

**Translator Metrics**:
- Characters translated
- Successful calls
- Failed calls
- Quota usage

**Storage Metrics**:
- Used capacity
- Number of blobs by language
- Transaction count
- Egress data

### Logging Levels

```properties
# Production
logging.level.com.example.speechapp=INFO

# Development/Debug
logging.level.com.example.speechapp=DEBUG
logging.level.com.example.speechapp.service.TranslationService=DEBUG
logging.level.com.azure=INFO
```

### Dashboard Example

```
SPEECH DIARIZATION DASHBOARD
============================
Active Jobs: 3
Completed Today: 47
Failed Today: 2
Success Rate: 95.7%

LANGUAGE STATISTICS:
- English: 28 jobs (59.6%)
- Spanish: 8 jobs (17.0%)
- Hindi: 5 jobs (10.6%)
- French: 4 jobs (8.5%)
- Others: 2 jobs (4.3%)

Top Speakers Detected:
- Speaker 1: 1,245 segments
- Speaker 2: 892 segments
- Speaker 3: 456 segments

Storage Used: 2.3 GB / 5 GB
Azure Costs (MTD): $67.89
  - Speech: $45.67
  - Translator: $12.34
  - Storage: $9.88
```

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📞 Support

For issues and questions:
- Check the [Troubleshooting](#troubleshooting) section
- Review logs in `logs/speech-diarization.log`
- Create a GitHub issue with:
    - Error message
    - Logs
    - Steps to reproduce
    - Azure region and tier information

---

**Built with** 💙 using Spring Boot, Azure Speech, and Azure Translator

**Version**: 2.0.0 (Multi-language Support)  
**Last Updated**: March 2026  
**Java Version**: 17  
**Spring Boot**: 3.1.5

**Repository**: [https://github.com/your-repo/speech-diarization-app](https://github.com/your-repo/speech-diarization-app)

---

*Happy transcribing!* 🎤
```

This complete README.md includes:
- ✅ Azure setup at the beginning
- ✅ Quick start with git clone and maven commands
- ✅ All features listed
- ✅ Configuration tables with Azure portal locations
- ✅ Language support and translation details
- ✅ Java classes overview table
- ✅ Detailed class descriptions with purposes and methods
- ✅ API endpoints and REST API examples
- ✅ Comprehensive usage guide with upload page, status page, and results page
- ✅ How to view both original and translated transcripts with language toggle
- ✅ File naming convention with language suffixes
- ✅ Troubleshooting table with common issues
- ✅ Security features
- ✅ Performance considerations with tables
- ✅ Monitoring metrics and dashboard example
- ✅ Proper markdown formatting throughout

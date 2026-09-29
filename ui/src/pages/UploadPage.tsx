import { useRef, useState } from 'react'
import type { ChangeEvent, DragEvent, FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import { getErrorMessage } from '../api/errors'
import { AppShell } from '../components/AppShell'
import type { DocumentRecord } from '../types/document'

const extensionTypes: Record<string, string> = { '.csv': 'CSV' }
const supportedExtensions = Object.keys(extensionTypes)
const maxFileSize = 10 * 1024 * 1024

function typeForFile(file: File) {
  const extension = supportedExtensions.find((candidate) => file.name.toLowerCase().endsWith(candidate))
  return extension ? extensionTypes[extension] : ''
}

function isSupportedFile(file: File) {
  return typeForFile(file) !== ''
}

function formatFileSize(size: number) {
  return size < 1024 * 1024
    ? `${Math.max(1, Math.round(size / 1024))} KB`
    : `${(size / (1024 * 1024)).toFixed(1)} MB`
}

export function UploadPage() {
  const fileInput = useRef<HTMLInputElement>(null)
  const [file, setFile] = useState<File | null>(null)
  const [type, setType] = useState('')
  const [isDragging, setIsDragging] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState<DocumentRecord | null>(null)
  const [progress, setProgress] = useState<number | null>(null)
  const [isUploading, setIsUploading] = useState(false)

  function selectFile(nextFile?: File) {
    setError('')
    setSuccess(null)
    setProgress(null)

    if (!nextFile) return
    if (!isSupportedFile(nextFile)) {
      setFile(null)
      setError('Choose a CSV file.')
      return
    }
    if (nextFile.size > maxFileSize) {
      setFile(null)
      setError('Files must be 10 MB or smaller.')
      return
    }

    setFile(nextFile)
    setType(typeForFile(nextFile))
  }

  function handleFileChange(event: ChangeEvent<HTMLInputElement>) {
    selectFile(event.target.files?.[0])
  }

  function handleDrop(event: DragEvent<HTMLDivElement>) {
    event.preventDefault()
    setIsDragging(false)
    selectFile(event.dataTransfer.files[0])
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setSuccess(null)

    if (!file) {
      setError('Choose a file to upload.')
      return
    }
    if (!type) {
      setError('Select a document type before uploading.')
      return
    }
    const expectedType = typeForFile(file)
    if (expectedType && type !== expectedType) {
      setError(`This looks like a ${expectedType} file. Select ${expectedType} as the document type.`)
      return
    }

    const formData = new FormData()
    formData.append('file', file)
    formData.append('type', type)
    setIsUploading(true)
    setProgress(0)

    try {
      const response = await api.post<DocumentRecord>('/documents', formData, {
        onUploadProgress: (event) => {
          if (event.total) setProgress(Math.round((event.loaded / event.total) * 100))
        },
      })
      setSuccess(response.data)
      setProgress(100)
      setFile(null)
      setType('')
      if (fileInput.current) fileInput.current.value = ''
    } catch (requestError) {
      setProgress(null)
      setError(getErrorMessage(requestError, 'The upload failed. Please try again.'))
    } finally {
      setIsUploading(false)
    }
  }

  return (
    <AppShell>
      <section className="page-heading">
        <div>
          <h1>Upload a document</h1>
          <p>Add a financial document to extract and organize its transactions.</p>
        </div>
      </section>

      <div className="upload-layout">
        <form className="surface upload-card" onSubmit={handleSubmit}>
          <div className="section-heading">
            <h2>Choose a file</h2>
            <p>Select a statement from your device.</p>
          </div>

          <input
            ref={fileInput}
            className="visually-hidden"
            type="file"
            accept=".csv"
            onChange={handleFileChange}
            aria-label="Choose a CSV file"
          />
          <div
            className={`drop-zone${isDragging ? ' dragging' : ''}${file ? ' has-file' : ''}`}
            onDragEnter={(event) => { event.preventDefault(); setIsDragging(true) }}
            onDragOver={(event) => { event.preventDefault(); setIsDragging(true) }}
            onDragLeave={(event) => {
              if (!event.currentTarget.contains(event.relatedTarget as Node | null)) setIsDragging(false)
            }}
            onDrop={handleDrop}
          >
            {file ? (
              <>
                <span className="file-icon" aria-hidden="true">▤</span>
                <strong className="selected-filename">{file.name}</strong>
                <span className="drop-hint">{formatFileSize(file.size)} · Ready to upload</span>
                <button className="button button-secondary" type="button" onClick={() => fileInput.current?.click()}>
                  Choose a different file
                </button>
              </>
            ) : (
              <>
                <span className="upload-icon" aria-hidden="true">
                  <svg viewBox="0 0 24 24" fill="none"><path d="M12 17V5m-5 5 5-5 5 5M5 18v1a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-1" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" /></svg>
                </span>
                <strong>Drag and drop your file here</strong>
                <span className="drop-hint">or browse files on your device</span>
                <button className="button button-secondary" type="button" onClick={() => fileInput.current?.click()}>
                  Browse files
                </button>
              </>
            )}
          </div>
          <p className="supported-formats">Supported format: CSV · Maximum file size: 10 MB</p>

          <label className="field upload-type-field">
            <span>Document type</span>
            <select value={type} onChange={(event) => setType(event.target.value)} required>
              <option value="" disabled>Select document type</option>
              <option value="CSV">CSV</option>
            </select>
          </label>

          {error && <div className="notice notice-error" role="alert">{error}</div>}
          {success && (
            <div className="notice notice-success" role="status">
              <strong>{success.originalFilename} uploaded successfully.</strong>
              <span>Your document is now {success.status.toLowerCase()}.</span>
              <Link to="/documents">View your documents</Link>
            </div>
          )}
          {progress !== null && (
            <div className="upload-progress" aria-live="polite">
              <div className="progress-label"><span>{isUploading ? 'Uploading document' : 'Upload complete'}</span><span>{progress}%</span></div>
              <div className="progress-track" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={progress}>
                <span style={{ width: `${progress}%` }} />
              </div>
            </div>
          )}
          <button className="button button-primary upload-submit" type="submit" disabled={isUploading}>
            {isUploading ? 'Uploading…' : 'Upload document'}
          </button>
        </form>

        <aside className="upload-aside">
          <section className="surface next-steps">
            <h2><span className="aside-check" aria-hidden="true">✓</span> What happens next?</h2>
            <ol>
              <li>Your file is securely uploaded.</li>
              <li>We extract the transactions.</li>
              <li>Review them in your document list.</li>
            </ol>
            <p>You can track processing status at any time.</p>
          </section>
          <section className="privacy-card">
            <span className="privacy-lock" aria-hidden="true">▣</span>
            <div><strong>Your documents stay private</strong><p>Files are only available to your account.</p></div>
          </section>
        </aside>
      </div>
    </AppShell>
  )
}

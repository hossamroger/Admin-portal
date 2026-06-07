import { ChangeDetectionStrategy, Component, ElementRef, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpEventType } from '@angular/common/http';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTooltipModule } from '@angular/material/tooltip';

import { ApiService } from '../../core/api.service';
import { NotifyService } from '../../core/notify.service';
import { UploadResult } from '../../core/models';

@Component({
  selector: 'app-attachments',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    FormsModule,
    MatButtonModule, MatFormFieldModule, MatIconModule,
    MatInputModule, MatProgressBarModule, MatTooltipModule,
  ],
  templateUrl: './attachments.html',
  styleUrl: './attachments.scss',
})
export class AttachmentsComponent {
  private readonly api    = inject(ApiService);
  private readonly notify = inject(NotifyService);

  private readonly fileInput = viewChild.required<ElementRef<HTMLInputElement>>('fileInput');

  // ---- form state ----
  readonly selectedFile  = signal<File | null>(null);
  readonly folder        = signal('');
  readonly documentName  = signal('');

  // ---- upload state ----
  readonly uploading  = signal(false);
  readonly progress   = signal(0);
  readonly result     = signal<UploadResult | null>(null);
  readonly uploadError = signal('');

  // ---- copy state ----
  readonly copied = signal(false);

  // -----------------------------------------------------------------------

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file  = input.files?.[0] ?? null;
    this.selectedFile.set(file);
    this.result.set(null);
    this.uploadError.set('');
    this.progress.set(0);
    // Pre-fill document name with original filename (user can override).
    if (file && !this.documentName()) {
      this.documentName.set(file.name);
    }
  }

  clearFile(): void {
    this.selectedFile.set(null);
    this.result.set(null);
    this.uploadError.set('');
    this.progress.set(0);
    this.fileInput().nativeElement.value = '';
  }

  get canUpload(): boolean {
    return !!this.selectedFile() && !!this.folder().trim() && !this.uploading();
  }

  upload(): void {
    const file   = this.selectedFile();
    const folder = this.folder().trim();
    if (!file || !folder || this.uploading()) return;

    this.uploading.set(true);
    this.uploadError.set('');
    this.result.set(null);
    this.progress.set(0);

    this.api.uploadAttachment(file, folder, this.documentName().trim()).subscribe({
      next: event => {
        if (event.type === HttpEventType.UploadProgress && event.total) {
          this.progress.set(Math.round((event.loaded / event.total) * 100));
        }
        if (event.type === HttpEventType.Response) {
          this.result.set(event.body!);
          this.uploading.set(false);
          this.progress.set(100);
          this.notify.success('File uploaded successfully');
        }
      },
      error: err => {
        this.uploading.set(false);
        this.progress.set(0);
        const msg = err?.error?.error || err?.message || 'Upload failed';
        this.uploadError.set(msg);
        this.notify.error(err, 'Upload failed');
      },
    });
  }

  async copyUrl(): Promise<void> {
    const url = this.result()?.url;
    if (!url) return;
    try {
      await navigator.clipboard.writeText(url);
      this.copied.set(true);
      this.notify.success('URL copied to clipboard');
      setTimeout(() => this.copied.set(false), 2500);
    } catch {
      this.notify.error(null, 'Copy not supported in this browser');
    }
  }

  formatBytes(bytes: number): string {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(2) + ' MB';
  }
}

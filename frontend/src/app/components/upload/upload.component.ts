import { Component, inject, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { NotificationService } from '../../services/notification.service';
import { UploadSuccessResponse } from '../../models/types';

@Component({
  selector: 'app-upload',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './upload.component.html',
  styleUrl: './upload.component.scss'
})
export class UploadComponent {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly notificationService = inject(NotificationService);
  private readonly cdr = inject(ChangeDetectorRef);

  isDragging = false;
  isUploading = false;
  uploadResult: UploadSuccessResponse | null = null;
  errorMessage: string | null = null;

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragging = true;
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragging = false;
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragging = false;

    if (event.dataTransfer?.files && event.dataTransfer.files.length > 0) {
      const file = event.dataTransfer.files[0];
      this.uploadFile(file);
    }
  }

  onFileSelected(event: any): void {
    const file = event.target.files?.[0];
    if (file) {
      this.uploadFile(file);
    }
  }

  uploadFile(file: File): void {
    if (!file.name.toLowerCase().endsWith('.pdf')) {
      const msg = 'Csak .pdf kiterjesztésű számla tölthető fel!';
      this.errorMessage = msg;
      this.notificationService.warning(msg, 'Érvénytelen fájlformátum');
      return;
    }

    this.isUploading = true;
    this.errorMessage = null;
    this.uploadResult = null;

    this.api.uploadInvoice(file).subscribe({
      next: (res) => {
        this.isUploading = false;
        this.uploadResult = res;
        this.notificationService.success(res.message, 'Számla sikeresen rögzítve');
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.isUploading = false;
        const detailMsg = err.error?.detail || err.error?.message || 'Hiba történt a fájl feltöltése és feldolgozása közben.';
        this.errorMessage = detailMsg;
        this.cdr.markForCheck();
      }
    });
  }

  goToInventory(): void {
    this.router.navigate(['/receiving']);
  }
}

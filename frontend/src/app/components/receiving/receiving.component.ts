import { Component, OnInit, inject, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { NotificationService } from '../../services/notification.service';
import { InvoiceSummary, InvoiceDetail, InvoiceItemDetail, ReceiveInvoiceItemRequest } from '../../models/types';

@Component({
  selector: 'app-receiving',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './receiving.component.html',
  styleUrl: './receiving.component.scss'
})
export class ReceivingComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly notificationService = inject(NotificationService);
  private readonly cdr = inject(ChangeDetectorRef);

  invoices: InvoiceSummary[] = [];
  selectedInvoice: InvoiceDetail | null = null;
  isLoadingInvoices = true;
  isLoadingDetail = false;
  isSubmitting = false;

  // Receive Modal
  showReceiveModal = false;
  activeItem: InvoiceItemDetail | null = null;
  receiveQuantity = 1;
  receiveSerial = '';
  receiveWipeCode = '';

  ngOnInit(): void {
    this.loadInvoices();
  }

  loadInvoices(): void {
    this.isLoadingInvoices = true;
    this.cdr.markForCheck();
    this.api.getInvoices().subscribe({
      next: (data) => {
        // Show only ACTIVE invoices (not fully deducted / cancelled)
        this.invoices = data.filter(inv => inv.status === 'ACTIVE');
        this.isLoadingInvoices = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.isLoadingInvoices = false;
        this.cdr.markForCheck();
      }
    });
  }

  selectInvoice(inv: InvoiceSummary): void {
    this.isLoadingDetail = true;
    this.cdr.markForCheck();
    this.api.getInvoiceById(inv.id).subscribe({
      next: (detail) => {
        this.selectedInvoice = detail;
        this.isLoadingDetail = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.isLoadingDetail = false;
        this.cdr.markForCheck();
      }
    });
  }

  clearSelection(): void {
    this.selectedInvoice = null;
    this.cdr.markForCheck();
  }

  // Computed helpers
  get totalInvoicedUnits(): number {
    return this.selectedInvoice?.items.reduce((acc, it) => acc + it.initialQuantity, 0) ?? 0;
  }

  get totalRemainingUnits(): number {
    return this.selectedInvoice?.items.reduce((acc, it) => acc + it.currentQuantity, 0) ?? 0;
  }

  openReceiveModal(item: InvoiceItemDetail): void {
    this.activeItem = item;
    this.receiveQuantity = item.currentQuantity > 0 ? 1 : 0;
    this.receiveSerial = '';
    this.receiveWipeCode = '';
    this.showReceiveModal = true;
    this.cdr.markForCheck();
  }

  closeReceiveModal(): void {
    this.showReceiveModal = false;
    this.activeItem = null;
    this.cdr.markForCheck();
  }

  submitReceive(): void {
    if (!this.activeItem || !this.selectedInvoice) return;
    this.isSubmitting = true;
    this.cdr.markForCheck();

    const request: ReceiveInvoiceItemRequest = {
      invoiceItemId: this.activeItem.id,
      quantity: this.receiveQuantity,
      serialNumber: this.receiveSerial.trim() || null,
      wipeCode: this.receiveWipeCode.trim() || null
    };

    this.api.receiveInvoiceItem(request).subscribe({
      next: (created) => {
        this.notificationService.success(
          `${created.length} db "${this.activeItem?.productName}" raktárra véve.`,
          'Átvétel sikeres'
        );
        this.closeReceiveModal();
        this.isSubmitting = false;
        // Reload detail to update currentQuantity
        this.selectInvoice({ id: this.selectedInvoice!.id } as InvoiceSummary);
        this.loadInvoices();
      },
      error: () => {
        this.isSubmitting = false;
        this.cdr.markForCheck();
      }
    });
  }
}

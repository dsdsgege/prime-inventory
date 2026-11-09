import { Component, OnInit, inject, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { NotificationService } from '../../services/notification.service';
import { InvoiceSummary, InvoiceDetail, InventoryItemView, InventoryStatus, UpdateStatusRequest } from '../../models/types';

@Component({
  selector: 'app-inventory',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './inventory.component.html',
  styleUrl: './inventory.component.scss'
})
export class InventoryComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly notificationService = inject(NotificationService);
  private readonly cdr = inject(ChangeDetectorRef);

  invoices: InvoiceSummary[] = [];
  items: InventoryItemView[] = [];
  statusFilter: InventoryStatus | '' = '';
  searchQuery = '';
  isLoading = true;
  isLoadingInvoices = true;

  // Invoice detail panel
  selectedInvoice: InvoiceDetail | null = null;
  isLoadingDetail = false;

  ngOnInit(): void {
    this.loadInvoices();
    this.loadInventory();
  }

  loadInvoices(): void {
    this.isLoadingInvoices = true;
    this.cdr.markForCheck();
    this.api.getInvoices().subscribe({
      next: (data) => {
        this.invoices = data;
        this.isLoadingInvoices = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.isLoadingInvoices = false;
        this.cdr.markForCheck();
      }
    });
  }

  loadInventory(): void {
    this.isLoading = true;
    this.cdr.markForCheck();
    this.api.getInventory({
      status: this.statusFilter || null,
      search: this.searchQuery.trim() || null
    }).subscribe({
      next: (data) => {
        this.items = data;
        this.isLoading = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.isLoading = false;
        this.cdr.markForCheck();
      }
    });
  }

  viewInvoiceDetail(inv: InvoiceSummary): void {
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

  clearInvoiceDetail(): void {
    this.selectedInvoice = null;
    this.cdr.markForCheck();
  }

  cancelInvoice(inv: InvoiceSummary): void {
    if (!confirm(`Biztosan törölni szeretné a(z) "${inv.invoiceNumber}" számlát? Ez a művelet visszavonhatatlan!`)) return;
    this.api.cancelInvoice(inv.id).subscribe({
      next: () => {
        this.notificationService.success(`"${inv.invoiceNumber}" számla törölve.`);
        this.selectedInvoice = null;
        this.loadInvoices();
        this.loadInventory();
      }
    });
  }

  updateStatus(item: InventoryItemView, status: InventoryStatus): void {
    const request: UpdateStatusRequest = { status };
    this.api.updateInventoryStatus(item.id, request).subscribe({
      next: () => {
        this.notificationService.info(`"${item.productName}" állapota: ${status}`);
        this.loadInventory();
      }
    });
  }

  // Computed
  get inStockCount(): number {
    return this.items.filter(it => it.status === 'IN_STOCK').length;
  }

  get soldCount(): number {
    return this.items.filter(it => it.status === 'SOLD').length;
  }
}

import { Component, OnInit, inject, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { NotificationService } from '../../services/notification.service';
import {
  Supplier,
  Invoice,
  InventoryItem,
  ReceivePayload,
  CancelReceivePayload,
  BatchReceiveItemEntry,
  BatchCancelReceiveItemEntry
} from '../../models/types';

export interface BatchReceiveRow {
  item: InventoryItem;
  quantity: number;
  expectedMarginPercent: number | null;
  selectedWipeCode: string | null;
}

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

  items: InventoryItem[] = [];
  suppliers: Supplier[] = [];
  invoices: Invoice[] = [];
  filteredInvoices: Invoice[] = [];

  selectedSupplierId: number | null = null;
  selectedInvoiceId: number | null = null;
  searchQuery: string = '';
  onlyPending: boolean = false;
  isLoading: boolean = true;

  // Pagination
  currentPage = 0;
  pageSize = 25;
  totalElements = 0;
  totalPages = 1;

  // Selection for Batch Operations
  selectedItemIds = new Set<number>();

  // Quick Percentage Presets for Expected Selling Price
  readonly marginPresets: number[] = [10, 15, 20, 25, 30, 40];

  // Single Receive Modal
  showReceiveModal: boolean = false;
  activeItem: InventoryItem | null = null;
  receiveQuantity: number = 1;
  expectedMarginPercent: number | null = null;
  selectedWipeCode: string | null = null;
  receiveNote: string = '';
  isSubmitting: boolean = false;

  // Batch Receive Modal
  showBatchReceiveModal: boolean = false;
  batchRows: BatchReceiveRow[] = [];
  batchPresetMargin: number | null = null;
  batchNote: string = '';

  // Codes View Modal
  showCodesModal: boolean = false;
  modalCodesItem: InventoryItem | null = null;

  ngOnInit(): void {
    this.loadItems();
    this.loadSuppliers();
    this.loadInvoices();
  }

  loadSuppliers(): void {
    this.api.getSuppliers().subscribe({
      next: (data) => {
        this.suppliers = data;
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Failed to load suppliers', err);
        this.cdr.markForCheck();
      }
    });
  }

  loadInvoices(): void {
    this.api.getInvoices().subscribe({
      next: (data) => {
        this.invoices = data;
        this.updateFilteredInvoices();
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Failed to load invoices', err);
        this.cdr.markForCheck();
      }
    });
  }

  onSupplierChange(): void {
    this.selectedInvoiceId = null;
    this.currentPage = 0;
    this.updateFilteredInvoices();
    this.loadItems();
  }

  onFilterChange(): void {
    this.currentPage = 0;
    this.loadItems();
  }

  updateFilteredInvoices(): void {
    if (this.selectedSupplierId) {
      this.filteredInvoices = this.invoices.filter(i => i.supplier_id === this.selectedSupplierId);
    } else {
      this.filteredInvoices = [...this.invoices];
    }
    this.cdr.markForCheck();
  }

  loadItems(): void {
    this.isLoading = true;
    this.cdr.markForCheck();
    this.api.getReceivingItemsPage({
      supplier_id: this.selectedSupplierId,
      invoice_id: this.selectedInvoiceId,
      status: this.onlyPending ? 'PENDING' : null,
      search: this.searchQuery ? this.searchQuery.trim() : null,
      page: this.currentPage,
      size: this.pageSize
    }).subscribe({
      next: (page) => {
        this.items = page.content;
        this.totalElements = page.totalElements;
        this.totalPages = Math.max(1, page.totalPages);
        this.selectedItemIds.clear();
        this.isLoading = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Failed to load items', err);
        this.isLoading = false;
        this.cdr.markForCheck();
      }
    });
  }

  goToPage(page: number): void {
    if (page < 0 || page >= this.totalPages) return;
    this.currentPage = page;
    this.loadItems();
  }

  get totalInvoicedUnits(): number {
    return this.items.reduce((acc, it) => acc + it.initial_quantity, 0);
  }

  get totalReceivedUnits(): number {
    return this.items.reduce((acc, it) => acc + it.received_quantity, 0);
  }

  get totalPendingUnits(): number {
    return this.items.reduce((acc, it) => acc + it.pending_quantity, 0);
  }

  // ==========================================
  // Multi-Select Helpers
  // ==========================================
  isSelected(itemId: number): boolean {
    return this.selectedItemIds.has(itemId);
  }

  toggleSelection(itemId: number): void {
    if (this.selectedItemIds.has(itemId)) {
      this.selectedItemIds.delete(itemId);
    } else {
      this.selectedItemIds.add(itemId);
    }
    this.cdr.markForCheck();
  }

  get isAllSelected(): boolean {
    return this.items.length > 0 && this.items.every(it => this.selectedItemIds.has(it.id));
  }

  toggleSelectAll(): void {
    if (this.isAllSelected) {
      this.selectedItemIds.clear();
    } else {
      this.items.forEach(it => this.selectedItemIds.add(it.id));
    }
    this.cdr.markForCheck();
  }

  selectAllPending(): void {
    this.selectedItemIds.clear();
    this.items.filter(it => it.pending_quantity > 0).forEach(it => this.selectedItemIds.add(it.id));
    this.cdr.markForCheck();
  }

  clearSelection(): void {
    this.selectedItemIds.clear();
    this.cdr.markForCheck();
  }

  get selectedPendingItems(): InventoryItem[] {
    return this.items.filter(it => this.selectedItemIds.has(it.id) && it.pending_quantity > 0);
  }

  get selectedReceivedItems(): InventoryItem[] {
    return this.items.filter(it => this.selectedItemIds.has(it.id) && it.received_quantity > 0 && it.current_quantity > 0);
  }

  // ==========================================
  // Single Receive & Cancel
  // ==========================================
  openReceiveModal(item: InventoryItem): void {
    this.activeItem = item;
    this.receiveQuantity = item.pending_quantity;
    this.expectedMarginPercent = item.expected_margin_percent && item.expected_margin_percent > 0
      ? item.expected_margin_percent
      : null;
    this.selectedWipeCode = null;
    this.receiveNote = '';
    this.showReceiveModal = true;
    this.cdr.markForCheck();
  }

  closeReceiveModal(): void {
    this.showReceiveModal = false;
    this.activeItem = null;
    this.expectedMarginPercent = null;
    this.cdr.markForCheck();
  }

  setExpectedMargin(pct: number): void {
    this.expectedMarginPercent = pct;
    this.cdr.markForCheck();
  }

  get isSingleMarginValid(): boolean {
    return this.expectedMarginPercent !== null && !isNaN(this.expectedMarginPercent) && this.expectedMarginPercent > 0;
  }

  get calculatedExpectedUnitPrice(): number {
    if (!this.activeItem || !this.isSingleMarginValid || !this.expectedMarginPercent) return 0;
    return Math.round((this.activeItem.unit_price_gross || 0) * (1 + this.expectedMarginPercent / 100));
  }

  get calculatedExpectedUnitProfit(): number {
    if (!this.activeItem || !this.isSingleMarginValid) return 0;
    return this.calculatedExpectedUnitPrice - (this.activeItem.unit_price_gross || 0);
  }

  viewAllCodes(item: InventoryItem): void {
    this.modalCodesItem = item;
    this.showCodesModal = true;
    this.cdr.markForCheck();
  }

  closeCodesModal(): void {
    this.showCodesModal = false;
    this.modalCodesItem = null;
    this.cdr.markForCheck();
  }

  copyCode(code: string): void {
    navigator.clipboard?.writeText(code);
    this.notificationService.info(`Kód másolva a vágólapra: ${code}`);
  }

  submitReceive(): void {
    if (!this.activeItem) return;
    if (!this.isSingleMarginValid || !this.expectedMarginPercent) {
      this.notificationService.warning('Kérjük, adja meg a várható eladási árrést (%-ban) az átvételhez!');
      return;
    }
    this.isSubmitting = true;
    this.cdr.markForCheck();

    const payload: ReceivePayload = {
      quantity: this.receiveQuantity,
      expected_margin_percent: this.expectedMarginPercent,
      confirmed_wipe_codes: this.selectedWipeCode ? [this.selectedWipeCode] : undefined,
      note: this.receiveNote ? this.receiveNote.trim() : null
    };

    this.api.receiveItem(this.activeItem.id, payload).subscribe({
      next: (res) => {
        this.notificationService.success(
          `Átvétel sikeres: ${this.activeItem?.product_name} (+${this.receiveQuantity} db, várható ár: ${this.calculatedExpectedUnitPrice.toLocaleString()} Ft/db).`,
          'Raktárra beérkezett'
        );
        this.closeReceiveModal();
        this.isSubmitting = false;
        this.loadItems();
        this.loadSuppliers();
        this.loadInvoices();
        this.cdr.markForCheck();
      },
      error: () => {
        this.isSubmitting = false;
        this.cdr.markForCheck();
      }
    });
  }

  cancelReceive(item: InventoryItem): void {
    const payload: CancelReceivePayload = {
      quantity: 1,
      note: 'Átvétel visszavonása (-1 db)'
    };

    this.api.cancelReceive(item.id, payload).subscribe({
      next: () => {
        this.notificationService.success(
          `Visszavonás sikeres: ${item.product_name} (-1 db raktárról visszavéve).`,
          'Átvétel visszavonva'
        );
        this.loadItems();
        this.loadSuppliers();
        this.loadInvoices();
        this.cdr.markForCheck();
      },
      error: () => {
        this.cdr.markForCheck();
      }
    });
  }

  // ==========================================
  // Batch Receive & Batch Cancel
  // ==========================================
  openBatchReceiveModal(): void {
    const pending = this.selectedPendingItems;
    if (pending.length === 0) {
      this.notificationService.warning('A kijelölt tételek között nincs átvételre váró termék.');
      return;
    }
    this.batchPresetMargin = null;
    this.batchRows = pending.map(it => ({
      item: it,
      quantity: it.pending_quantity,
      expectedMarginPercent: it.expected_margin_percent && it.expected_margin_percent > 0
        ? it.expected_margin_percent
        : null,
      selectedWipeCode: null
    }));
    this.batchNote = '';
    this.showBatchReceiveModal = true;
    this.cdr.markForCheck();
  }

  closeBatchReceiveModal(): void {
    this.showBatchReceiveModal = false;
    this.batchRows = [];
    this.batchPresetMargin = null;
    this.cdr.markForCheck();
  }

  applyBatchPresetMargin(pct: number): void {
    this.batchPresetMargin = pct;
    this.batchRows.forEach(r => {
      r.expectedMarginPercent = pct;
    });
    this.cdr.markForCheck();
  }

  setRowMargin(row: BatchReceiveRow, pct: number): void {
    row.expectedMarginPercent = pct;
    this.cdr.markForCheck();
  }

  calcRowExpectedUnitPrice(row: BatchReceiveRow): number {
    if (!row.expectedMarginPercent || row.expectedMarginPercent <= 0) return 0;
    return Math.round((row.item.unit_price_gross || 0) * (1 + row.expectedMarginPercent / 100));
  }

  calcRowExpectedUnitProfit(row: BatchReceiveRow): number {
    if (!row.expectedMarginPercent || row.expectedMarginPercent <= 0) return 0;
    return this.calcRowExpectedUnitPrice(row) - (row.item.unit_price_gross || 0);
  }

  get isBatchMarginValid(): boolean {
    const activeRows = this.batchRows.filter(r => r.quantity > 0);
    return activeRows.length > 0 && activeRows.every(
      r => r.expectedMarginPercent !== null && !isNaN(r.expectedMarginPercent) && r.expectedMarginPercent > 0
    );
  }

  get totalBatchReceiveUnits(): number {
    return this.batchRows.reduce((acc, r) => acc + (r.quantity || 0), 0);
  }

  submitBatchReceive(): void {
    if (this.batchRows.length === 0) return;
    if (!this.isBatchMarginValid) {
      this.notificationService.warning('Minden átveendő tételhez kötelező megadni a várható eladási árrést (%-ban)!');
      return;
    }
    this.isSubmitting = true;
    this.cdr.markForCheck();

    const entries: BatchReceiveItemEntry[] = this.batchRows
      .filter(r => r.quantity > 0)
      .map(r => ({
        item_id: r.item.id,
        quantity: r.quantity,
        expected_margin_percent: r.expectedMarginPercent!,
        confirmed_wipe_codes: r.selectedWipeCode ? [r.selectedWipeCode] : (r.item.wipe_codes?.length ? r.item.wipe_codes : undefined),
        note: this.batchNote ? this.batchNote.trim() : null
      }));

    this.api.batchReceiveItems({
      items: entries,
      note: this.batchNote ? this.batchNote.trim() : null
    }).subscribe({
      next: (res) => {
        this.notificationService.success(res.message, 'Csoportos átvétel kész');
        this.closeBatchReceiveModal();
        this.isSubmitting = false;
        this.loadItems();
        this.loadSuppliers();
        this.loadInvoices();
        this.cdr.markForCheck();
      },
      error: () => {
        this.isSubmitting = false;
        this.cdr.markForCheck();
      }
    });
  }

  submitBatchCancel(): void {
    const received = this.selectedReceivedItems;
    if (received.length === 0) {
      this.notificationService.warning('A kijelölt tételek között nincs visszavonható raktári tétel.');
      return;
    }
    if (!confirm(`Biztosan visszavonja a kijelölt ${received.length} tétel raktári átvételét?`)) {
      return;
    }

    const entries: BatchCancelReceiveItemEntry[] = received.map(it => ({
      item_id: it.id,
      quantity: Math.min(it.received_quantity, it.current_quantity),
      note: 'Csoportos átvétel visszavonása'
    }));

    this.api.batchCancelReceive({ items: entries, note: 'Csoportos átvétel visszavonása' }).subscribe({
      next: (res) => {
        this.notificationService.success(res.message, 'Csoportos visszavonás kész');
        this.loadItems();
        this.loadSuppliers();
        this.loadInvoices();
        this.cdr.markForCheck();
      }
    });
  }
}

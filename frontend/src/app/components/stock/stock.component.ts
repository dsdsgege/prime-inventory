import { Component, OnInit, inject, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { NotificationService } from '../../services/notification.service';
import {
  Supplier,
  Invoice,
  InventoryItem,
  SalePayload,
  BatchSaleItemEntry
} from '../../models/types';

export interface BatchSaleRow {
  item: InventoryItem;
  quantity: number;
  salePrice: number;
  selectedWipeCode: string | null;
}

@Component({
  selector: 'app-stock',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './stock.component.html',
  styleUrl: './stock.component.scss'
})
export class StockComponent implements OnInit {
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
  onlyInStock: boolean = true;
  isLoading: boolean = true;

  // Pagination
  currentPage = 0;
  pageSize = 25;
  totalElements = 0;
  totalPages = 1;

  // Multi-Select for Batch Sale
  selectedItemIds = new Set<number>();

  // Single Sale Modal
  showSaleModal: boolean = false;
  activeItem: InventoryItem | null = null;
  saleQuantity: number = 1;
  salePrice: number | null = null;
  selectedWipeCode: string | null = null;
  customerName: string = '';
  saleNote: string = '';
  isSubmitting: boolean = false;

  // Batch Sale Modal
  showBatchSaleModal: boolean = false;
  batchSaleRows: BatchSaleRow[] = [];
  batchCustomerName: string = '';
  batchSaleNote: string = '';

  // Codes View Modal
  showCodesModal: boolean = false;
  modalCodesItem: InventoryItem | null = null;

  ngOnInit(): void {
    this.loadStock();
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
    this.loadStock();
  }

  onFilterChange(): void {
    this.currentPage = 0;
    this.loadStock();
  }

  updateFilteredInvoices(): void {
    if (this.selectedSupplierId) {
      this.filteredInvoices = this.invoices.filter(i => i.supplier_id === this.selectedSupplierId);
    } else {
      this.filteredInvoices = [...this.invoices];
    }
    this.cdr.markForCheck();
  }

  loadStock(): void {
    this.isLoading = true;
    this.cdr.markForCheck();
    this.api.getStockItemsPage({
      supplier_id: this.selectedSupplierId,
      invoice_id: this.selectedInvoiceId,
      only_in_stock: this.onlyInStock,
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
        console.error('Failed to load stock', err);
        this.isLoading = false;
        this.cdr.markForCheck();
      }
    });
  }

  goToPage(page: number): void {
    if (page < 0 || page >= this.totalPages) return;
    this.currentPage = page;
    this.loadStock();
  }

  get totalAvailableUnits(): number {
    return this.items.reduce((acc, it) => acc + it.current_quantity, 0);
  }

  get totalStockValueGross(): number {
    return this.items.reduce((acc, it) => acc + (it.current_quantity * it.unit_price_gross), 0);
  }

  get totalSoldUnits(): number {
    return this.items.reduce((acc, it) => acc + it.sold_quantity, 0);
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

  get selectableItems(): InventoryItem[] {
    return this.items.filter(it => it.current_quantity > 0);
  }

  get isAllSelected(): boolean {
    const selectable = this.selectableItems;
    return selectable.length > 0 && selectable.every(it => this.selectedItemIds.has(it.id));
  }

  toggleSelectAll(): void {
    if (this.isAllSelected) {
      this.selectedItemIds.clear();
    } else {
      this.selectableItems.forEach(it => this.selectedItemIds.add(it.id));
    }
    this.cdr.markForCheck();
  }

  clearSelection(): void {
    this.selectedItemIds.clear();
    this.cdr.markForCheck();
  }

  get selectedInStockItems(): InventoryItem[] {
    return this.items.filter(it => this.selectedItemIds.has(it.id) && it.current_quantity > 0);
  }

  // ==========================================
  // Single Sale Modal
  // ==========================================
  openSaleModal(item: InventoryItem): void {
    this.activeItem = item;
    this.saleQuantity = 1;
    const markupPrice = Math.round(item.unit_price_gross * 1.15 / 1000) * 1000;
    this.salePrice = markupPrice;
    this.selectedWipeCode = item.wipe_codes?.length ? item.wipe_codes[0] : null;
    this.customerName = '';
    this.saleNote = '';
    this.showSaleModal = true;
    this.cdr.markForCheck();
  }

  closeSaleModal(): void {
    this.showSaleModal = false;
    this.activeItem = null;
    this.cdr.markForCheck();
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

  get estimatedProfit(): number {
    if (!this.activeItem || !this.salePrice) return 0;
    const totalCost = this.activeItem.unit_price_gross * this.saleQuantity;
    return this.salePrice - totalCost;
  }

  get estimatedMarginPercent(): number {
    if (!this.salePrice || this.salePrice <= 0) return 0;
    return (this.estimatedProfit / this.salePrice) * 100;
  }

  submitSale(): void {
    if (!this.activeItem || !this.salePrice) return;
    this.isSubmitting = true;
    this.cdr.markForCheck();

    const payload: SalePayload = {
      quantity: this.saleQuantity,
      sale_price: this.salePrice,
      used_wipe_code: this.selectedWipeCode,
      customer_name: this.customerName ? this.customerName.trim() : null,
      note: this.saleNote ? this.saleNote.trim() : null
    };

    this.api.sellItem(this.activeItem.id, payload).subscribe({
      next: (res) => {
        this.notificationService.success(
          `Sikeres értékesítés: ${this.activeItem?.product_name} (${res.sale_price.toLocaleString()} Ft, Haszon: +${res.profit.toLocaleString()} Ft).`,
          'Eladás rögzítve'
        );
        this.closeSaleModal();
        this.isSubmitting = false;
        this.loadStock();
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

  // ==========================================
  // Batch Sale Modal
  // ==========================================
  openBatchSaleModal(): void {
    const selected = this.selectedInStockItems;
    if (selected.length === 0) {
      this.notificationService.warning('Válasszon ki legalább 1 készleten lévő terméket az eladáshoz!');
      return;
    }
    this.batchSaleRows = selected.map(it => ({
      item: it,
      quantity: 1,
      salePrice: Math.round(it.unit_price_gross * 1.15 / 1000) * 1000,
      selectedWipeCode: it.wipe_codes?.length ? it.wipe_codes[0] : null
    }));
    this.batchCustomerName = '';
    this.batchSaleNote = '';
    this.showBatchSaleModal = true;
    this.cdr.markForCheck();
  }

  closeBatchSaleModal(): void {
    this.showBatchSaleModal = false;
    this.batchSaleRows = [];
    this.cdr.markForCheck();
  }

  get batchTotalCost(): number {
    return this.batchSaleRows.reduce((acc, r) => acc + (r.item.unit_price_gross * (r.quantity || 1)), 0);
  }

  get batchTotalRevenue(): number {
    return this.batchSaleRows.reduce((acc, r) => acc + (r.salePrice || 0), 0);
  }

  get batchTotalProfit(): number {
    return this.batchTotalRevenue - this.batchTotalCost;
  }

  submitBatchSale(): void {
    if (this.batchSaleRows.length === 0) return;
    this.isSubmitting = true;
    this.cdr.markForCheck();

    const entries: BatchSaleItemEntry[] = this.batchSaleRows.map(r => ({
      item_id: r.item.id,
      quantity: r.quantity,
      sale_price: r.salePrice,
      used_wipe_code: r.selectedWipeCode,
      note: this.batchSaleNote ? this.batchSaleNote.trim() : null
    }));

    this.api.batchSellItems({
      items: entries,
      customer_name: this.batchCustomerName ? this.batchCustomerName.trim() : null,
      note: this.batchSaleNote ? this.batchSaleNote.trim() : null
    }).subscribe({
      next: (res) => {
        this.notificationService.success(res.message, 'Csoportos eladás rögzítve');
        this.closeBatchSaleModal();
        this.isSubmitting = false;
        this.loadStock();
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
}

import { Component, OnInit, inject, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { NotificationService } from '../../services/notification.service';
import { Supplier, Invoice, InventoryItem, DeductPayload, RestorePayload } from '../../models/types';

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

  items: InventoryItem[] = [];
  suppliers: Supplier[] = [];
  invoices: Invoice[] = [];
  filteredInvoices: Invoice[] = [];

  selectedSupplierId: number | null = null;
  selectedInvoiceId: number | null = null;
  searchQuery: string = '';
  onlyAvailable: boolean = true;
  isLoading: boolean = true;

  // Deduct Modal
  showDeductModal: boolean = false;
  activeItem: InventoryItem | null = null;
  deductQuantity: number = 1;
  selectedWipeCode: string | null = null;
  deductNote: string = '';
  isSubmitting: boolean = false;

  ngOnInit(): void {
    this.loadInventory();
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
    this.updateFilteredInvoices();
    this.loadInventory();
  }

  updateFilteredInvoices(): void {
    if (this.selectedSupplierId) {
      this.filteredInvoices = this.invoices.filter(i => i.supplier_id === this.selectedSupplierId);
    } else {
      this.filteredInvoices = [...this.invoices];
    }
    this.cdr.markForCheck();
  }

  loadInventory(): void {
    this.isLoading = true;
    this.cdr.markForCheck();
    this.api.getInventory({
      supplier_id: this.selectedSupplierId,
      invoice_id: this.selectedInvoiceId,
      only_available: this.onlyAvailable,
      search: this.searchQuery ? this.searchQuery.trim() : null
    }).subscribe({
      next: (data) => {
        this.items = data;
        this.isLoading = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Failed to load inventory', err);
        this.isLoading = false;
        this.cdr.markForCheck();
      }
    });
  }

  get totalInStockUnits(): number {
    return this.items.reduce((acc, it) => acc + it.current_quantity, 0);
  }

  get depletedItemsCount(): number {
    return this.items.filter(it => it.current_quantity === 0).length;
  }

  openDeductModal(item: InventoryItem): void {
    this.activeItem = item;
    this.deductQuantity = 1;
    this.selectedWipeCode = null;
    this.deductNote = '';
    this.showDeductModal = true;
    this.cdr.markForCheck();
  }

  closeDeductModal(): void {
    this.showDeductModal = false;
    this.activeItem = null;
    this.cdr.markForCheck();
  }

  submitDeduct(): void {
    if (!this.activeItem) return;
    this.isSubmitting = true;
    this.cdr.markForCheck();

    const payload: DeductPayload = {
      quantity: this.deductQuantity,
      used_wipe_code: this.selectedWipeCode,
      note: this.deductNote ? this.deductNote.trim() : null
    };

    this.api.deductItem(this.activeItem.id, payload).subscribe({
      next: (res) => {
        this.notificationService.success(
          `Sikeres levonás: ${this.activeItem?.product_name} (-${this.deductQuantity} db). Új készlet: ${res.quantity_after} db.`,
          'Készlet levonva'
        );
        this.closeDeductModal();
        this.isSubmitting = false;
        this.loadInventory();
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

  restoreItem(item: InventoryItem): void {
    const payload: RestorePayload = {
      quantity: 1,
      note: 'Felhasználói korrekció (+1 db)'
    };

    this.api.restoreItem(item.id, payload).subscribe({
      next: (res) => {
        this.notificationService.success(
          `Korrekció sikeres: ${item.product_name} (+1 db). Új készlet: ${res.quantity_after} db.`,
          'Készlet visszaállítva'
        );
        this.loadInventory();
        this.loadSuppliers();
        this.loadInvoices();
        this.cdr.markForCheck();
      },
      error: () => {
        this.cdr.markForCheck();
      }
    });
  }
}

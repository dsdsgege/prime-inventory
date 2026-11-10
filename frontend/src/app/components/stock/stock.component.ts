import { Component, OnInit, inject, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { NotificationService } from '../../services/notification.service';
import { InventoryItemView, InventoryStatus, SellItemRequest, UpdateStatusRequest } from '../../models/types';

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

  items: InventoryItemView[] = [];
  statusFilter: InventoryStatus | '' = 'IN_STOCK';
  searchQuery = '';
  isLoading = true;

  // Sale Modal
  showSaleModal = false;
  activeItem: InventoryItemView | null = null;
  sellingPriceGross: number | null = null;
  customerName = '';
  saleNotes = '';
  isSubmitting = false;

  ngOnInit(): void {
    this.loadStock();
  }

  loadStock(): void {
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

  // Computed statistics
  get inStockItems(): InventoryItemView[] {
    return this.items.filter(it => it.status === 'IN_STOCK');
  }

  get totalStockValueGross(): number {
    return this.inStockItems.reduce((acc, it) => acc + (it.acquisitionGrossPrice ?? 0), 0);
  }

  // Sale modal
  openSaleModal(item: InventoryItemView): void {
    this.activeItem = item;
    // Pre-fill with a 15% markup suggestion
    const cost = item.acquisitionGrossPrice ?? 0;
    this.sellingPriceGross = cost > 0 ? Math.round(cost * 1.15 / 1000) * 1000 : null;
    this.customerName = '';
    this.saleNotes = '';
    this.showSaleModal = true;
    this.cdr.markForCheck();
  }

  closeSaleModal(): void {
    this.showSaleModal = false;
    this.activeItem = null;
    this.cdr.markForCheck();
  }

  get estimatedProfit(): number {
    if (!this.activeItem || !this.sellingPriceGross) return 0;
    return this.sellingPriceGross - (this.activeItem.acquisitionGrossPrice ?? 0);
  }

  get estimatedMarginPercent(): number {
    if (!this.sellingPriceGross || this.sellingPriceGross <= 0) return 0;
    return (this.estimatedProfit / this.sellingPriceGross) * 100;
  }

  submitSale(): void {
    if (!this.activeItem || !this.sellingPriceGross) return;
    this.isSubmitting = true;
    this.cdr.markForCheck();

    const request: SellItemRequest = {
      inventoryItemId: this.activeItem.id,
      sellingPriceGross: this.sellingPriceGross,
      customerName: this.customerName.trim() || undefined,
      notes: this.saleNotes.trim() || undefined
    };

    this.api.sellItem(request).subscribe({
      next: (res) => {
        const profit = (res.sellingPriceGross ?? 0) - (res.acquisitionGrossPrice ?? 0);
        this.notificationService.success(
          `Értékesítve: "${this.activeItem?.productName}" – ${(res.sellingPriceGross ?? 0).toLocaleString('hu-HU')} Ft (haszon: ${profit.toLocaleString('hu-HU')} Ft)`,
          'Eladás rögzítve'
        );
        this.closeSaleModal();
        this.isSubmitting = false;
        this.loadStock();
      },
      error: () => {
        this.isSubmitting = false;
        this.cdr.markForCheck();
      }
    });
  }

  markDamaged(item: InventoryItemView): void {
    if (!confirm(`Biztosan "Sérült" állapotúra állítja a(z) "${item.productName}" terméket?`)) return;
    const request: UpdateStatusRequest = { status: 'DAMAGED', notes: 'Manuálisan sérültnek jelölve' };
    this.api.updateInventoryStatus(item.id, request).subscribe({
      next: () => {
        this.notificationService.info(`"${item.productName}" sérültnek jelölve.`);
        this.loadStock();
      }
    });
  }
}

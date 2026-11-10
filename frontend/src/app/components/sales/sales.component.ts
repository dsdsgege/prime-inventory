import { Component, OnInit, inject, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { NotificationService } from '../../services/notification.service';
import { Supplier, SalesReport, SaleItem, BatchSalesImportResult } from '../../models/types';

@Component({
  selector: 'app-sales',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './sales.component.html',
  styleUrl: './sales.component.scss'
})
export class SalesComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly notificationService = inject(NotificationService);
  private readonly cdr = inject(ChangeDetectorRef);

  report: SalesReport = {
    total_sales_count: 0,
    total_units_sold: 0,
    total_revenue: 0,
    total_cost: 0,
    total_profit: 0,
    profit_margin_percent: 0,
    sales: []
  };

  suppliers: Supplier[] = [];
  selectedSupplierId: number | null = null;
  dateFrom: string = '';
  dateTo: string = '';
  searchQuery: string = '';
  isLoading: boolean = true;

  // Pagination
  currentPage = 0;
  pageSize = 30;
  totalElements = 0;
  totalPages = 1;

  // Multi-Select for Batch Storno
  selectedSaleIds = new Set<number>();

  // Excel Import & Export
  isImporting = false;
  isExporting = false;
  importResult: BatchSalesImportResult | null = null;

  ngOnInit(): void {
    this.loadSuppliers();
    this.loadReport();
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

  onFilterChange(): void {
    this.currentPage = 0;
    this.loadReport();
  }

  loadReport(): void {
    this.isLoading = true;
    this.cdr.markForCheck();
    this.api.getSalesReport({
      supplier_id: this.selectedSupplierId,
      date_from: this.dateFrom || null,
      date_to: this.dateTo || null,
      search: this.searchQuery ? this.searchQuery.trim() : null,
      page: this.currentPage,
      size: this.pageSize
    }).subscribe({
      next: (data) => {
        this.report = data;
        this.totalElements = data.total_elements ?? data.total_sales_count;
        this.totalPages = Math.max(1, data.total_pages ?? 1);
        this.selectedSaleIds.clear();
        this.isLoading = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Failed to load sales report', err);
        this.isLoading = false;
        this.cdr.markForCheck();
      }
    });
  }

  goToPage(page: number): void {
    if (page < 0 || page >= this.totalPages) return;
    this.currentPage = page;
    this.loadReport();
  }

  // ==========================================
  // Multi-Select Helpers
  // ==========================================
  isSelected(saleId: number): boolean {
    return this.selectedSaleIds.has(saleId);
  }

  toggleSelection(saleId: number): void {
    if (this.selectedSaleIds.has(saleId)) {
      this.selectedSaleIds.delete(saleId);
    } else {
      this.selectedSaleIds.add(saleId);
    }
    this.cdr.markForCheck();
  }

  get isAllSelected(): boolean {
    return this.report.sales.length > 0 && this.report.sales.every(s => this.selectedSaleIds.has(s.id));
  }

  toggleSelectAll(): void {
    if (this.isAllSelected) {
      this.selectedSaleIds.clear();
    } else {
      this.report.sales.forEach(s => this.selectedSaleIds.add(s.id));
    }
    this.cdr.markForCheck();
  }

  clearSelection(): void {
    this.selectedSaleIds.clear();
    this.cdr.markForCheck();
  }

  // ==========================================
  // Single & Batch Storno
  // ==========================================
  cancelSale(sale: SaleItem): void {
    if (!confirm(`Biztosan törölni/sztornózni szeretné a(z) "${sale.product_name}" eladást? A termék azonnal visszakerül az aktív raktárkészletbe!`)) {
      return;
    }

    this.api.cancelSale(sale.id).subscribe({
      next: (res) => {
        this.notificationService.success(
          `Eladás sztornózva: ${sale.product_name} visszakerült a készletre (készlet: ${res.restored_stock} db).`,
          'Sztornó sikeres'
        );
        this.loadReport();
        this.cdr.markForCheck();
      },
      error: () => {
        this.cdr.markForCheck();
      }
    });
  }

  batchCancelSelected(): void {
    if (this.selectedSaleIds.size === 0) return;
    if (!confirm(`Biztosan sztornózni szeretné a kijelölt ${this.selectedSaleIds.size} db eladást? Minden érintett termék visszakerül az aktív raktárkészletbe!`)) {
      return;
    }

    const ids = Array.from(this.selectedSaleIds);
    this.api.batchCancelSales({ sale_ids: ids }).subscribe({
      next: (res) => {
        this.notificationService.success(res.message, 'Csoportos sztornó kész');
        this.loadReport();
        this.cdr.markForCheck();
      }
    });
  }

  // ==========================================
  // Excel Import & Export
  // ==========================================
  onImportFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;

    this.isImporting = true;
    this.importResult = null;
    this.cdr.markForCheck();

    this.api.importSalesSpreadsheet(file).subscribe({
      next: (res) => {
        this.isImporting = false;
        this.importResult = res;
        this.notificationService.success(
          `Excel import kész: ${res.orders_processed} rendelés, ${res.items_processed} tétel (${res.items_matched_with_stock} készlettel párosítva).`,
          'Eladások importálva'
        );
        this.loadReport();
        this.cdr.markForCheck();
      },
      error: () => {
        this.isImporting = false;
        this.cdr.markForCheck();
      }
    });
    input.value = '';
  }

  exportReport(): void {
    this.isExporting = true;
    this.cdr.markForCheck();

    this.api.exportProfitCsv({
      supplier_id: this.selectedSupplierId,
      date_from: this.dateFrom || null,
      date_to: this.dateTo || null,
      search: this.searchQuery ? this.searchQuery.trim() : null
    }).subscribe({
      next: (tsv) => {
        const blob = new Blob([tsv], { type: 'text/tab-separated-values;charset=utf-8;' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `haszon_kimutatas_${this.dateFrom || 'mind'}_${this.dateTo || 'mind'}.tsv`;
        a.click();
        URL.revokeObjectURL(url);
        this.isExporting = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.isExporting = false;
        this.cdr.markForCheck();
      }
    });
  }
}

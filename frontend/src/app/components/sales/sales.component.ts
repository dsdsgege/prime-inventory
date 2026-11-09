import { Component, OnInit, inject, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { NotificationService } from '../../services/notification.service';
import { SalesOrderView, ProfitSummary, BatchSalesImportResult } from '../../models/types';

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

  orders: SalesOrderView[] = [];
  summary: ProfitSummary | null = null;

  dateFrom = '';
  dateTo = '';
  isLoading = true;
  isExporting = false;

  // Excel import
  isImporting = false;
  importResult: BatchSalesImportResult | null = null;
  showImportResult = false;

  ngOnInit(): void {
    this.loadData();
  }

  loadData(): void {
    this.isLoading = true;
    this.cdr.markForCheck();

    const filters = {
      startDate: this.dateFrom || null,
      endDate: this.dateTo || null
    };

    this.api.getSalesOrders(filters).subscribe({
      next: (data) => {
        this.orders = data;
        this.isLoading = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.isLoading = false;
        this.cdr.markForCheck();
      }
    });

    this.api.getProfitSummary(filters).subscribe({
      next: (data) => {
        this.summary = data;
        this.cdr.markForCheck();
      }
    });
  }

  applyFilters(): void {
    this.loadData();
  }

  clearFilters(): void {
    this.dateFrom = '';
    this.dateTo = '';
    this.loadData();
  }

  exportCsv(): void {
    this.isExporting = true;
    this.cdr.markForCheck();
    this.api.exportProfitCsv({
      startDate: this.dateFrom || null,
      endDate: this.dateTo || null
    }).subscribe({
      next: (csv) => {
        const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `profit_${this.dateFrom || 'all'}_${this.dateTo || 'all'}.csv`;
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

  onImportFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    this.importSales(file);
    // reset input so same file can be re-selected
    input.value = '';
  }

  importSales(file: File): void {
    this.isImporting = true;
    this.importResult = null;
    this.showImportResult = false;
    this.cdr.markForCheck();

    this.api.importSalesSpreadsheet(file).subscribe({
      next: (result) => {
        this.isImporting = false;
        this.importResult = result;
        this.showImportResult = true;
        this.notificationService.success(
          `Importálás kész: ${result.ordersProcessed} rendelés, ${result.itemsProcessed} tétel feldolgozva.`,
          'Excel importálva'
        );
        this.loadData();
        this.cdr.markForCheck();
      },
      error: () => {
        this.isImporting = false;
        this.cdr.markForCheck();
      }
    });
  }

  dismissImportResult(): void {
    this.showImportResult = false;
    this.importResult = null;
    this.cdr.markForCheck();
  }
}

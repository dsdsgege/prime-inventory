import { Component, OnInit, inject, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { TransactionLog, Supplier, Invoice } from '../../models/types';

@Component({
  selector: 'app-audit-log',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './audit-log.component.html',
  styleUrl: './audit-log.component.scss'
})
export class AuditLogComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly cdr = inject(ChangeDetectorRef);

  logs: TransactionLog[] = [];
  suppliers: Supplier[] = [];
  invoices: Invoice[] = [];
  selectedAction: string = '';
  selectedSupplierId: number | null = null;
  selectedInvoiceId: number | null = null;
  dateFrom: string = '';
  dateTo: string = '';
  isLoading: boolean = true;

  // Pagination
  currentPage = 0;
  pageSize = 30;
  totalElements = 0;
  totalPages = 1;

  ngOnInit(): void {
    this.loadLogs();
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
        console.error(err);
        this.cdr.markForCheck();
      }
    });
  }

  loadInvoices(): void {
    this.api.getInvoices().subscribe({
      next: (data) => {
        this.invoices = data;
        this.cdr.markForCheck();
      }
    });
  }

  onFilterChange(): void {
    this.currentPage = 0;
    this.loadLogs();
  }

  loadLogs(): void {
    this.isLoading = true;
    this.cdr.markForCheck();
    this.api.getLogsPage({
      action_type: this.selectedAction || null,
      supplier_id: this.selectedSupplierId,
      invoice_id: this.selectedInvoiceId,
      date_from: this.dateFrom || null,
      date_to: this.dateTo || null,
      page: this.currentPage,
      size: this.pageSize
    }).subscribe({
      next: (page) => {
        this.logs = page.content;
        this.totalElements = page.totalElements;
        this.totalPages = Math.max(1, page.totalPages);
        this.isLoading = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error(err);
        this.isLoading = false;
        this.cdr.markForCheck();
      }
    });
  }

  goToPage(page: number): void {
    if (page < 0 || page >= this.totalPages) return;
    this.currentPage = page;
    this.loadLogs();
  }
}

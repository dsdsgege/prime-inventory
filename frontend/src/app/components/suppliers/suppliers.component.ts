import { Component, OnInit, inject, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { Supplier, Invoice } from '../../models/types';

@Component({
  selector: 'app-suppliers',
  standalone: true,
  imports: [CommonModule, RouterLink, FormsModule],
  templateUrl: './suppliers.component.html',
  styleUrl: './suppliers.component.scss'
})
export class SuppliersComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly cdr = inject(ChangeDetectorRef);

  suppliers: Supplier[] = [];
  invoices: Invoice[] = [];
  selectedSupplier: Supplier | null = null;
  searchQuery = '';
  isLoadingSuppliers = true;
  isLoadingInvoices = true;

  // Pagination
  currentPage = 0;
  pageSize = 20;
  totalElements = 0;
  totalPages = 1;

  ngOnInit(): void {
    this.loadSuppliers();
    this.loadInvoices();
  }

  loadSuppliers(): void {
    this.isLoadingSuppliers = true;
    this.cdr.markForCheck();
    this.api.getSuppliers().subscribe({
      next: (data) => {
        this.suppliers = data;
        this.isLoadingSuppliers = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Failed to load suppliers', err);
        this.isLoadingSuppliers = false;
        this.cdr.markForCheck();
      }
    });
  }

  loadInvoices(): void {
    this.isLoadingInvoices = true;
    this.cdr.markForCheck();
    const supplierId = this.selectedSupplier ? this.selectedSupplier.id : null;
    this.api.getInvoicesPage({
      supplier_id: supplierId,
      search: this.searchQuery ? this.searchQuery.trim() : null,
      page: this.currentPage,
      size: this.pageSize
    }).subscribe({
      next: (page) => {
        this.invoices = page.content;
        this.totalElements = page.totalElements;
        this.totalPages = Math.max(1, page.totalPages);
        this.isLoadingInvoices = false;
        this.cdr.markForCheck();
      },
      error: (err) => {
        console.error('Failed to load invoices', err);
        this.isLoadingInvoices = false;
        this.cdr.markForCheck();
      }
    });
  }

  selectSupplier(s: Supplier): void {
    if (this.selectedSupplier?.id === s.id) {
      this.selectedSupplier = null;
    } else {
      this.selectedSupplier = s;
    }
    this.currentPage = 0;
    this.cdr.markForCheck();
    this.loadInvoices();
  }

  clearSupplierSelection(): void {
    this.selectedSupplier = null;
    this.currentPage = 0;
    this.cdr.markForCheck();
    this.loadInvoices();
  }

  onSearchChange(): void {
    this.currentPage = 0;
    this.loadInvoices();
  }

  goToPage(page: number): void {
    if (page < 0 || page >= this.totalPages) return;
    this.currentPage = page;
    this.loadInvoices();
  }

  get totalInStockAcrossAll(): number {
    return this.suppliers.reduce((acc, s) => acc + (s.total_in_stock_qty || 0), 0);
  }
}

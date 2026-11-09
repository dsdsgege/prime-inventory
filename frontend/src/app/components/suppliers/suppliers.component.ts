import { Component, OnInit, inject, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ApiService } from '../../services/api.service';
import { InvoiceSummary } from '../../models/types';

export interface DerivedSupplier {
  id?: number;
  name: string;
  taxNumber: string;
  euTaxNumber?: string;
  address?: string;
  bankAccount?: string;
  invoiceCount: number;
  totalItems: number;
  totalInStockQty: number;
}

@Component({
  selector: 'app-suppliers',
  standalone: true,
  imports: [CommonModule, RouterLink],
  templateUrl: './suppliers.component.html',
  styleUrl: './suppliers.component.scss'
})
export class SuppliersComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly cdr = inject(ChangeDetectorRef);

  invoices: InvoiceSummary[] = [];
  selectedSupplier: DerivedSupplier | null = null;
  isLoadingSuppliers = true;
  isLoadingInvoices = true;
  totalInStockAcrossAll = 0;

  ngOnInit(): void {
    this.loadInvoices();
  }

  loadInvoices(): void {
    this.isLoadingInvoices = true;
    this.isLoadingSuppliers = true;
    this.cdr.markForCheck();

    this.api.getInvoices().subscribe({
      next: (data) => {
        this.invoices = data;
        this.isLoadingInvoices = false;
        this.isLoadingSuppliers = false;
        this.calculateMetrics();
        this.cdr.markForCheck();
      },
      error: () => {
        this.isLoadingInvoices = false;
        this.isLoadingSuppliers = false;
        this.cdr.markForCheck();
      }
    });
  }

  get suppliers(): DerivedSupplier[] {
    const map = new Map<string, DerivedSupplier>();
    for (const inv of this.invoices) {
      const name = inv.supplierName || 'Ismeretlen beszállító';
      const entry = map.get(name) ?? {
        name,
        taxNumber: 'N/A',
        invoiceCount: 0,
        totalItems: 0,
        totalInStockQty: 0
      };
      entry.invoiceCount++;
      entry.totalItems += inv.itemsCount || 0;
      entry.totalInStockQty += inv.totalCurrentQuantity || 0;
      map.set(name, entry);
    }
    return Array.from(map.values());
  }

  calculateMetrics(): void {
    this.totalInStockAcrossAll = this.invoices.reduce((acc, inv) => acc + (inv.totalCurrentQuantity || 0), 0);
  }

  selectSupplier(supplier: DerivedSupplier): void {
    this.selectedSupplier = this.selectedSupplier?.name === supplier.name ? null : supplier;
    this.cdr.markForCheck();
  }

  clearSupplierSelection(): void {
    this.selectedSupplier = null;
    this.cdr.markForCheck();
  }
}

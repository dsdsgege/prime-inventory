import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  InvoiceSummary,
  InvoiceDetail,
  InventoryItemView,
  InventoryStatus,
  ReceiveInvoiceItemRequest,
  SellItemRequest,
  UpdateStatusRequest,
  SalesOrderView,
  ProfitSummary,
  BatchSalesImportResult,
} from '../models/types';

@Injectable({
  providedIn: 'root'
})
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api';

  // ----------------------------------------------------------
  // Invoices  –  /api/invoices
  // ----------------------------------------------------------

  /** POST /api/invoices/upload  –  Upload a Számlázz.hu PDF */
  uploadInvoice(file: File): Observable<InvoiceDetail> {
    const form = new FormData();
    form.append('file', file, file.name);
    return this.http.post<InvoiceDetail>(`${this.base}/invoices/upload`, form);
  }

  /** GET /api/invoices */
  getInvoices(): Observable<InvoiceSummary[]> {
    return this.http.get<InvoiceSummary[]>(`${this.base}/invoices`);
  }

  /** GET /api/invoices/{id} */
  getInvoiceById(id: number): Observable<InvoiceDetail> {
    return this.http.get<InvoiceDetail>(`${this.base}/invoices/${id}`);
  }

  /** DELETE /api/invoices/{id}  –  Cancel/delete an invoice */
  cancelInvoice(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/invoices/${id}`);
  }

  // ----------------------------------------------------------
  // Inventory  –  /api/inventory
  // ----------------------------------------------------------

  /** GET /api/inventory  –  List inventory items (optional status/search filter) */
  getInventory(filters?: {
    status?: InventoryStatus | null;
    search?: string | null;
  }): Observable<InventoryItemView[]> {
    let params = new HttpParams();
    if (filters?.status) params = params.set('status', filters.status);
    if (filters?.search?.trim()) params = params.set('search', filters.search.trim());
    return this.http.get<InventoryItemView[]>(`${this.base}/inventory`, { params });
  }

  /** POST /api/inventory/receive  –  Receive items from an invoice into stock */
  receiveInvoiceItem(request: ReceiveInvoiceItemRequest): Observable<InventoryItemView[]> {
    return this.http.post<InventoryItemView[]>(`${this.base}/inventory/receive`, request);
  }

  /** POST /api/inventory/sell  –  Record a manual sale from stock */
  sellItem(request: SellItemRequest): Observable<InventoryItemView> {
    return this.http.post<InventoryItemView>(`${this.base}/inventory/sell`, request);
  }

  /** PATCH /api/inventory/{id}/status  –  Update inventory item status */
  updateInventoryStatus(id: number, request: UpdateStatusRequest): Observable<InventoryItemView> {
    return this.http.patch<InventoryItemView>(`${this.base}/inventory/${id}/status`, request);
  }

  // ----------------------------------------------------------
  // Sales  –  /api/sales
  // ----------------------------------------------------------

  /** POST /api/sales/import  –  Import a webshop sales Excel/CSV export */
  importSalesSpreadsheet(file: File): Observable<BatchSalesImportResult> {
    const form = new FormData();
    form.append('file', file, file.name);
    return this.http.post<BatchSalesImportResult>(`${this.base}/sales/import`, form);
  }

  /** GET /api/sales/orders  –  List sales orders, optionally filtered by date range */
  getSalesOrders(filters?: {
    startDate?: string | null;
    endDate?: string | null;
  }): Observable<SalesOrderView[]> {
    let params = new HttpParams();
    if (filters?.startDate) params = params.set('startDate', filters.startDate);
    if (filters?.endDate) params = params.set('endDate', filters.endDate);
    return this.http.get<SalesOrderView[]>(`${this.base}/sales/orders`, { params });
  }

  /** GET /api/sales/profit-summary */
  getProfitSummary(filters?: {
    startDate?: string | null;
    endDate?: string | null;
  }): Observable<ProfitSummary> {
    let params = new HttpParams();
    if (filters?.startDate) params = params.set('startDate', filters.startDate);
    if (filters?.endDate) params = params.set('endDate', filters.endDate);
    return this.http.get<ProfitSummary>(`${this.base}/sales/profit-summary`, { params });
  }

  /**
   * GET /api/sales/export  –  Download profit CSV for the given date range.
   * Returns the raw text so the caller can trigger a browser download.
   */
  exportProfitCsv(filters?: {
    startDate?: string | null;
    endDate?: string | null;
  }): Observable<string> {
    let params = new HttpParams();
    if (filters?.startDate) params = params.set('startDate', filters.startDate);
    if (filters?.endDate) params = params.set('endDate', filters.endDate);
    return this.http.get(`${this.base}/sales/export`, {
      params,
      responseType: 'text'
    });
  }
}

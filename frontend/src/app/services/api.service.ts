import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  PageResponse,
  Supplier,
  Invoice,
  InventoryItem,
  ReceivePayload,
  BatchReceivePayload,
  CancelReceivePayload,
  BatchCancelReceivePayload,
  SalePayload,
  BatchSalePayload,
  SalesReport,
  BatchCancelSalePayload,
  BatchOperationResponse,
  BatchSalesImportResult,
  DeductPayload,
  RestorePayload,
  TransactionLog,
  UploadSuccessResponse
} from '../models/types';

function isValidFilter(val: any): boolean {
  if (val === null || val === undefined) return false;
  const s = String(val).trim().toLowerCase();
  return s !== '' && s !== 'null' && s !== 'undefined' && s !== '0' && s !== 'none';
}

function appendPageParams(params: HttpParams, page?: number | null, size?: number | null): HttpParams {
  if (page !== null && page !== undefined && page >= 0) {
    params = params.set('page', String(page));
  }
  if (size !== null && size !== undefined && size > 0) {
    params = params.set('size', String(size));
  }
  return params;
}

@Injectable({
  providedIn: 'root'
})
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = '/api';

  getSuppliers(): Observable<Supplier[]> {
    return this.http.get<Supplier[]>(`${this.baseUrl}/suppliers`);
  }

  getInvoices(filters?: {
    supplier_id?: number | string | null;
    status?: string | null;
    search?: string | null;
    page?: number | null;
    size?: number | null;
  }): Observable<Invoice[]> {
    let params = new HttpParams();
    if (isValidFilter(filters?.supplier_id)) params = params.set('supplier_id', String(filters!.supplier_id));
    if (isValidFilter(filters?.status)) params = params.set('status', String(filters!.status));
    if (isValidFilter(filters?.search)) params = params.set('search', String(filters!.search));
    params = appendPageParams(params, filters?.page, filters?.size);
    return this.http.get<Invoice[]>(`${this.baseUrl}/invoices`, { params });
  }

  getInvoicesPage(filters?: {
    supplier_id?: number | string | null;
    status?: string | null;
    search?: string | null;
    page?: number | null;
    size?: number | null;
  }): Observable<PageResponse<Invoice>> {
    let params = new HttpParams();
    if (isValidFilter(filters?.supplier_id)) params = params.set('supplier_id', String(filters!.supplier_id));
    if (isValidFilter(filters?.status)) params = params.set('status', String(filters!.status));
    if (isValidFilter(filters?.search)) params = params.set('search', String(filters!.search));
    params = appendPageParams(params, filters?.page, filters?.size);
    return this.http.get<PageResponse<Invoice>>(`${this.baseUrl}/invoices/page`, { params });
  }

  // Stage 1: Invoices & Receiving
  getReceivingItems(filters?: {
    supplier_id?: number | string | null;
    invoice_id?: number | string | null;
    status?: string | null;
    search?: string | null;
    page?: number | null;
    size?: number | null;
  }): Observable<InventoryItem[]> {
    let params = new HttpParams();
    if (isValidFilter(filters?.supplier_id)) params = params.set('supplier_id', String(filters!.supplier_id));
    if (isValidFilter(filters?.invoice_id)) params = params.set('invoice_id', String(filters!.invoice_id));
    if (isValidFilter(filters?.status)) params = params.set('status', String(filters!.status));
    if (isValidFilter(filters?.search)) params = params.set('search', String(filters!.search));
    params = appendPageParams(params, filters?.page, filters?.size);
    return this.http.get<InventoryItem[]>(`${this.baseUrl}/receiving`, { params });
  }

  getReceivingItemsPage(filters?: {
    supplier_id?: number | string | null;
    invoice_id?: number | string | null;
    status?: string | null;
    search?: string | null;
    page?: number | null;
    size?: number | null;
  }): Observable<PageResponse<InventoryItem>> {
    let params = new HttpParams();
    if (isValidFilter(filters?.supplier_id)) params = params.set('supplier_id', String(filters!.supplier_id));
    if (isValidFilter(filters?.invoice_id)) params = params.set('invoice_id', String(filters!.invoice_id));
    if (isValidFilter(filters?.status)) params = params.set('status', String(filters!.status));
    if (isValidFilter(filters?.search)) params = params.set('search', String(filters!.search));
    params = appendPageParams(params, filters?.page, filters?.size);
    return this.http.get<PageResponse<InventoryItem>>(`${this.baseUrl}/receiving/page`, { params });
  }

  receiveItem(itemId: number, payload: ReceivePayload): Observable<any> {
    return this.http.post(`${this.baseUrl}/receiving/${itemId}/receive`, payload);
  }

  batchReceiveItems(payload: BatchReceivePayload): Observable<BatchOperationResponse> {
    return this.http.post<BatchOperationResponse>(`${this.baseUrl}/receiving/batch-receive`, payload);
  }

  cancelReceive(itemId: number, payload: CancelReceivePayload): Observable<any> {
    return this.http.post(`${this.baseUrl}/receiving/${itemId}/cancel`, payload);
  }

  batchCancelReceive(payload: BatchCancelReceivePayload): Observable<BatchOperationResponse> {
    return this.http.post<BatchOperationResponse>(`${this.baseUrl}/receiving/batch-cancel`, payload);
  }

  // Stage 2: Store Stock & Sales
  getStockItems(filters?: {
    supplier_id?: number | string | null;
    invoice_id?: number | string | null;
    only_in_stock?: boolean;
    search?: string | null;
    page?: number | null;
    size?: number | null;
  }): Observable<InventoryItem[]> {
    let params = new HttpParams();
    if (isValidFilter(filters?.supplier_id)) params = params.set('supplier_id', String(filters!.supplier_id));
    if (isValidFilter(filters?.invoice_id)) params = params.set('invoice_id', String(filters!.invoice_id));
    if (filters?.only_in_stock !== undefined) params = params.set('only_in_stock', String(filters.only_in_stock));
    if (isValidFilter(filters?.search)) params = params.set('search', String(filters!.search));
    params = appendPageParams(params, filters?.page, filters?.size);
    return this.http.get<InventoryItem[]>(`${this.baseUrl}/stock`, { params });
  }

  getStockItemsPage(filters?: {
    supplier_id?: number | string | null;
    invoice_id?: number | string | null;
    only_in_stock?: boolean;
    search?: string | null;
    page?: number | null;
    size?: number | null;
  }): Observable<PageResponse<InventoryItem>> {
    let params = new HttpParams();
    if (isValidFilter(filters?.supplier_id)) params = params.set('supplier_id', String(filters!.supplier_id));
    if (isValidFilter(filters?.invoice_id)) params = params.set('invoice_id', String(filters!.invoice_id));
    if (filters?.only_in_stock !== undefined) params = params.set('only_in_stock', String(filters.only_in_stock));
    if (isValidFilter(filters?.search)) params = params.set('search', String(filters!.search));
    params = appendPageParams(params, filters?.page, filters?.size);
    return this.http.get<PageResponse<InventoryItem>>(`${this.baseUrl}/stock/page`, { params });
  }

  sellItem(itemId: number, payload: SalePayload): Observable<any> {
    return this.http.post(`${this.baseUrl}/stock/${itemId}/sell`, payload);
  }

  batchSellItems(payload: BatchSalePayload): Observable<BatchOperationResponse> {
    return this.http.post<BatchOperationResponse>(`${this.baseUrl}/stock/batch-sell`, payload);
  }

  // Stage 3: Sales Report & Profit Analytics
  getSalesReport(filters?: {
    supplier_id?: number | string | null;
    date_from?: string | null;
    date_to?: string | null;
    search?: string | null;
    page?: number | null;
    size?: number | null;
  }): Observable<SalesReport> {
    let params = new HttpParams();
    if (isValidFilter(filters?.supplier_id)) params = params.set('supplier_id', String(filters!.supplier_id));
    if (isValidFilter(filters?.date_from)) params = params.set('date_from', String(filters!.date_from));
    if (isValidFilter(filters?.date_to)) params = params.set('date_to', String(filters!.date_to));
    if (isValidFilter(filters?.search)) params = params.set('search', String(filters!.search));
    params = appendPageParams(params, filters?.page, filters?.size);
    return this.http.get<SalesReport>(`${this.baseUrl}/sales`, { params });
  }

  cancelSale(saleId: number): Observable<any> {
    return this.http.post(`${this.baseUrl}/sales/${saleId}/cancel`, {});
  }

  batchCancelSales(payload: BatchCancelSalePayload): Observable<BatchOperationResponse> {
    return this.http.post<BatchOperationResponse>(`${this.baseUrl}/sales/batch-cancel`, payload);
  }

  importSalesSpreadsheet(file: File): Observable<BatchSalesImportResult> {
    const formData = new FormData();
    formData.append('file', file, file.name);
    return this.http.post<BatchSalesImportResult>(`${this.baseUrl}/sales/import`, formData);
  }

  exportProfitCsv(filters?: {
    supplier_id?: number | string | null;
    date_from?: string | null;
    date_to?: string | null;
    search?: string | null;
  }): Observable<string> {
    let params = new HttpParams();
    if (isValidFilter(filters?.supplier_id)) params = params.set('supplier_id', String(filters!.supplier_id));
    if (isValidFilter(filters?.date_from)) params = params.set('date_from', String(filters!.date_from));
    if (isValidFilter(filters?.date_to)) params = params.set('date_to', String(filters!.date_to));
    if (isValidFilter(filters?.search)) params = params.set('search', String(filters!.search));
    return this.http.get(`${this.baseUrl}/sales/export`, {
      params,
      responseType: 'text'
    });
  }

  // Audit Logs
  getLogs(filters?: {
    supplier_id?: number | string | null;
    invoice_id?: number | string | null;
    inventory_item_id?: number | string | null;
    action_type?: string | null;
    date_from?: string | null;
    date_to?: string | null;
    page?: number | null;
    size?: number | null;
  }): Observable<TransactionLog[]> {
    let params = new HttpParams();
    if (isValidFilter(filters?.supplier_id)) params = params.set('supplier_id', String(filters!.supplier_id));
    if (isValidFilter(filters?.invoice_id)) params = params.set('invoice_id', String(filters!.invoice_id));
    if (isValidFilter(filters?.inventory_item_id)) params = params.set('inventory_item_id', String(filters!.inventory_item_id));
    if (isValidFilter(filters?.action_type)) params = params.set('action_type', String(filters!.action_type));
    if (isValidFilter(filters?.date_from)) params = params.set('date_from', String(filters!.date_from));
    if (isValidFilter(filters?.date_to)) params = params.set('date_to', String(filters!.date_to));
    params = appendPageParams(params, filters?.page, filters?.size);
    return this.http.get<TransactionLog[]>(`${this.baseUrl}/logs`, { params });
  }

  getLogsPage(filters?: {
    supplier_id?: number | string | null;
    invoice_id?: number | string | null;
    inventory_item_id?: number | string | null;
    action_type?: string | null;
    date_from?: string | null;
    date_to?: string | null;
    page?: number | null;
    size?: number | null;
  }): Observable<PageResponse<TransactionLog>> {
    let params = new HttpParams();
    if (isValidFilter(filters?.supplier_id)) params = params.set('supplier_id', String(filters!.supplier_id));
    if (isValidFilter(filters?.invoice_id)) params = params.set('invoice_id', String(filters!.invoice_id));
    if (isValidFilter(filters?.inventory_item_id)) params = params.set('inventory_item_id', String(filters!.inventory_item_id));
    if (isValidFilter(filters?.action_type)) params = params.set('action_type', String(filters!.action_type));
    if (isValidFilter(filters?.date_from)) params = params.set('date_from', String(filters!.date_from));
    if (isValidFilter(filters?.date_to)) params = params.set('date_to', String(filters!.date_to));
    params = appendPageParams(params, filters?.page, filters?.size);
    return this.http.get<PageResponse<TransactionLog>>(`${this.baseUrl}/logs/page`, { params });
  }

  uploadInvoice(file: File): Observable<UploadSuccessResponse> {
    const formData = new FormData();
    formData.append('file', file, file.name);
    return this.http.post<UploadSuccessResponse>(`${this.baseUrl}/invoices/upload`, formData);
  }

  // Legacy fallback
  getInventory(filters?: {
    supplier_id?: number | string | null;
    invoice_id?: number | string | null;
    only_available?: boolean;
    search?: string | null;
    page?: number | null;
    size?: number | null;
  }): Observable<InventoryItem[]> {
    return this.getReceivingItems({
      supplier_id: filters?.supplier_id,
      invoice_id: filters?.invoice_id,
      status: filters?.only_available ? 'PENDING' : null,
      search: filters?.search,
      page: filters?.page,
      size: filters?.size
    });
  }

  deductItem(itemId: number, payload: DeductPayload): Observable<any> {
    return this.receiveItem(itemId, {
      quantity: payload.quantity,
      expected_margin_percent: payload.expected_margin_percent ?? 15,
      confirmed_wipe_codes: payload.used_wipe_code ? [payload.used_wipe_code] : undefined,
      note: payload.note
    });
  }

  restoreItem(itemId: number, payload: RestorePayload): Observable<any> {
    return this.cancelReceive(itemId, {
      quantity: payload.quantity,
      note: payload.note
    });
  }
}

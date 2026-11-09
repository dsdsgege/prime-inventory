// ============================================================
// Auth DTOs  –  matches AuthDto.java
// ============================================================

export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  username: string;
  fullName: string;
  role: string;
}

export interface User {
  id?: number;
  username: string;
  fullName: string;
  role: string;
  active?: boolean;
}

// ============================================================
// Invoice DTOs  –  matches InvoiceDto.java
// ============================================================

export interface SupplierRef {
  id: number;
  name: string;
  taxNumber: string;
  euTaxNumber?: string | null;
  address?: string | null;
  bankAccount?: string | null;
}



export interface InvoiceItemDetail {
  id: number;
  lineIndex: number;
  productName: string;
  rawDescription?: string | null;
  unit: string;
  initialQuantity: number;
  currentQuantity: number;
  unitPriceNet: number;
  vatRate: string;
  totalNet: number;
  totalVat: number;
  totalGross: number;
  fullyDeducted: boolean;
  wipeCodes: string[];
}

/** Returned by GET /api/invoices */
export interface InvoiceSummary {
  id: number;
  invoiceNumber: string;
  supplierName: string;
  issueDate?: string | null;
  deliveryDate?: string | null;
  dueDate?: string | null;
  paymentMethod?: string | null;
  totalNetAmount?: number | null;
  totalVatAmount?: number | null;
  totalGrossAmount?: number | null;
  status: string;
  itemsCount: number;
}

/** Returned by GET /api/invoices/{id} and POST /api/invoices/upload */
export interface InvoiceDetail {
  id: number;
  invoiceNumber: string;
  supplier: SupplierRef;
  issueDate?: string | null;
  deliveryDate?: string | null;
  dueDate?: string | null;
  paymentMethod?: string | null;
  totalNetAmount?: number | null;
  totalVatAmount?: number | null;
  totalGrossAmount?: number | null;
  status: string;
  items: InvoiceItemDetail[];
}

// ============================================================
// Inventory DTOs  –  matches InventoryDto.java
// ============================================================

export type InventoryStatus = 'IN_STOCK' | 'SOLD' | 'RETURNED' | 'DAMAGED' | 'RESERVED';

export interface InventoryItemView {
  id: number;
  invoiceItemId: number;
  productName: string;
  serialNumber?: string | null;
  wipeCode?: string | null;
  acquisitionNetPrice?: number | null;
  acquisitionGrossPrice?: number | null;
  receivedAt?: string | null;
  status: InventoryStatus;
  sellingPriceNet?: number | null;
  sellingPriceGross?: number | null;
  soldAt?: string | null;
  notes?: string | null;
}

export interface ReceiveInvoiceItemRequest {
  invoiceItemId: number;
  quantity?: number;
  serialNumber?: string | null;
  wipeCode?: string | null;
}

export interface SellItemRequest {
  inventoryItemId: number;
  sellingPriceGross: number;
  sellingPriceNet?: number | null;
  customerName?: string | null;
  notes?: string | null;
}

export interface UpdateStatusRequest {
  status: InventoryStatus;
  notes?: string | null;
}

// ============================================================
// Sales DTOs  –  matches SalesDto.java
// ============================================================

export interface SaleItemView {
  id: number;
  salesOrderId: number;
  inventoryItemId: number;
  productName: string;
  quantity: number;
  sellingPriceGross: number;
  costPriceGross: number;
  profitGross: number;
  profitNet: number;
}

export interface SalesOrderView {
  id: number;
  orderNumber: string;
  orderDate: string;
  customerName?: string | null;
  totalSellingPriceGross: number;
  totalCostPriceGross: number;
  totalProfitGross: number;
  totalProfitNet: number;
  source: string;
  items: SaleItemView[];
}

export interface ProfitSummary {
  startDate?: string | null;
  endDate?: string | null;
  totalSalesGross: number;
  totalCostGross: number;
  totalProfitGross: number;
  totalProfitNet: number;
  totalOrdersCount: number;
  totalItemsSold: number;
}

export interface BatchSalesImportResult {
  ordersProcessed: number;
  itemsProcessed: number;
  itemsMatchedWithStock: number;
  itemsMissingCostPrice: number;
  totalSalesAmount: number;
  totalProfitCalculated: number;
  notices: string[];
}

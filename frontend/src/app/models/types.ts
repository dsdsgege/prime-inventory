// ============================================================
// Auth DTOs
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

export interface UserDto {
  id?: number;
  username: string;
  fullName: string;
  role: string;
  active: boolean;
}

// ============================================================
// Invoice DTOs
// ============================================================

export interface SupplierDto {
  id: number;
  name: string;
  taxNumber?: string;
  euTaxNumber?: string;
  address?: string;
  bankAccount?: string;
}

export interface InvoiceSummary {
  id: number;
  invoiceNumber: string;
  supplierName: string;
  issueDate?: string;
  deliveryDate?: string;
  dueDate?: string;
  paymentMethod?: string;
  totalNetAmount?: number;
  totalVatAmount?: number;
  totalGrossAmount?: number;
  status: string;
  itemsCount: number;
}

export interface InvoiceDetail {
  id: number;
  invoiceNumber: string;
  supplier: SupplierDto;
  issueDate?: string;
  deliveryDate?: string;
  dueDate?: string;
  paymentMethod?: string;
  totalNetAmount?: number;
  totalVatAmount?: number;
  totalGrossAmount?: number;
  status: string;
  items: InvoiceItemDetail[];
}

export interface InvoiceItemDetail {
  id: number;
  lineIndex: number;
  productName: string;
  rawDescription?: string;
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

// ============================================================
// Inventory DTOs
// ============================================================
export type InventoryStatus = 'IN_STOCK' | 'SOLD' | 'RETURNED' | 'DAMAGED' | 'RESERVED';

export interface InventoryItemView {
  id: number;
  invoiceItemId: number;
  productName: string;
  serialNumber?: string;
  wipeCode?: string;
  acquisitionNetPrice?: number;
  acquisitionGrossPrice?: number;
  receivedAt?: string;
  status: InventoryStatus;
  sellingPriceNet?: number;
  sellingPriceGross?: number;
  soldAt?: string;
  notes?: string;
}

export interface ReceiveInvoiceItemRequest {
  invoiceItemId: number;
  quantity?: number;
  serialNumber?: string;
  wipeCode?: string;
}

export interface SellItemRequest {
  inventoryItemId: number;
  sellingPriceGross: number;
  sellingPriceNet?: number;
  customerName?: string;
  notes?: string;
}

export interface UpdateStatusRequest {
  status: InventoryStatus;
  notes?: string;
}

// ============================================================
// Sales DTOs
// ============================================================
export interface SalesOrderView {
  id: number;
  orderNumber: string;
  orderDate: string;
  customerName?: string;
  totalSellingPriceGross: number;
  totalCostPriceGross: number;
  totalProfitGross: number;
  totalProfitNet: number;
  source: string;
  items: SaleItemView[];
}

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

export interface ProfitSummary {
  startDate?: string;
  endDate?: string;
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

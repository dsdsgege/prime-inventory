export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

export interface Supplier {
  id: number;
  name: string;
  tax_number: string;
  eu_tax_number?: string | null;
  address?: string | null;
  bank_account?: string | null;
  created_at: string;
  invoice_count: number;
  total_in_stock_qty: number;
  total_ordered_qty?: number;
  total_received_qty?: number;
}

export interface Invoice {
  id: number;
  supplier_id: number;
  supplier_name: string;
  invoice_number: string;
  issue_date?: string | null;
  delivery_date?: string | null;
  due_date?: string | null;
  payment_method?: string | null;
  total_net_amount?: number | null;
  total_vat_amount?: number | null;
  total_gross_amount?: number | null;
  pdf_filename: string;
  status: string;
  uploaded_at: string;
  total_initial_quantity: number;
  total_received_quantity?: number;
  total_current_quantity: number;
}

export interface InventoryItem {
  id: number;
  invoice_id: number;
  supplier_id: number;
  line_index: number;
  product_name: string;
  raw_description?: string | null;
  unit: string;
  initial_quantity: number;
  received_quantity: number;
  sold_quantity: number;
  current_quantity: number;
  pending_quantity: number;
  unit_price_net: number;
  unit_price_gross: number;
  vat_rate: string;
  total_net: number;
  total_vat: number;
  total_gross: number;
  status: string;
  received_at?: string | null;
  expected_margin_percent?: number | null;
  expected_sale_price?: number | null;
  is_fully_deducted: boolean;
  wipe_codes: string[];
  supplier_name?: string;
  invoice_number?: string;
}

export interface ReceivePayload {
  quantity: number;
  expected_margin_percent: number;
  confirmed_wipe_codes?: string[] | null;
  received_at?: string | null;
  note?: string | null;
}

export interface BatchReceiveItemEntry {
  item_id: number;
  quantity: number;
  expected_margin_percent: number;
  confirmed_wipe_codes?: string[] | null;
  note?: string | null;
}

export interface BatchReceivePayload {
  items: BatchReceiveItemEntry[];
  note?: string | null;
}

export interface CancelReceivePayload {
  quantity: number;
  note?: string | null;
}

export interface BatchCancelReceiveItemEntry {
  item_id: number;
  quantity: number;
  note?: string | null;
}

export interface BatchCancelReceivePayload {
  items: BatchCancelReceiveItemEntry[];
  note?: string | null;
}

export interface SalePayload {
  quantity: number;
  sale_price: number;
  used_wipe_code?: string | null;
  customer_name?: string | null;
  note?: string | null;
  sold_at?: string | null;
}

export interface BatchSaleItemEntry {
  item_id: number;
  quantity: number;
  sale_price: number;
  used_wipe_code?: string | null;
  note?: string | null;
}

export interface BatchSalePayload {
  items: BatchSaleItemEntry[];
  customer_name?: string | null;
  note?: string | null;
  sold_at?: string | null;
}

export interface SaleItem {
  id: number;
  inventory_item_id: number;
  invoice_id: number;
  supplier_id: number;
  order_number?: string | null;
  quantity: number;
  unit_cost_net: number;
  unit_cost_gross: number;
  sale_price: number;
  profit: number;
  profit_margin_percent: number;
  used_wipe_code?: string | null;
  customer_name?: string | null;
  note?: string | null;
  sold_at: string;
  created_at: string;
  product_name?: string;
  supplier_name?: string;
  invoice_number?: string;
}

export interface SalesReport {
  total_sales_count: number;
  total_units_sold: number;
  total_revenue: number;
  total_cost: number;
  total_profit: number;
  profit_margin_percent: number;
  sales: SaleItem[];
  total_elements?: number;
  total_pages?: number;
  page?: number;
  size?: number;
}

export interface BatchCancelSalePayload {
  sale_ids: number[];
}

export interface BatchOperationResponse {
  message: string;
  processed_count: number;
  total_quantity: number;
  results: any[];
}

export interface BatchSalesImportResult {
  orders_processed: number;
  items_processed: number;
  items_matched_with_stock: number;
  items_missing_cost_price: number;
  total_sales_amount: number;
  total_profit_calculated: number;
  notices: string[];
}

export interface DeductPayload {
  quantity: number;
  expected_margin_percent?: number;
  used_wipe_code?: string | null;
  note?: string | null;
}

export interface RestorePayload {
  quantity: number;
  note?: string | null;
}

export interface TransactionLog {
  id: number;
  inventory_item_id: number;
  invoice_id: number;
  supplier_id: number;
  action_type: string;
  quantity_changed: number;
  quantity_before: number;
  quantity_after: number;
  used_wipe_code?: string | null;
  note?: string | null;
  created_at: string;
  supplier_name?: string;
  invoice_number?: string;
  product_name?: string;
}

export interface UploadSuccessResponse {
  message: string;
  invoice_id: number | null;
  invoice_number: string;
  supplier_name: string;
  item_count: number;
  unit_count: number;
  expense_only?: boolean;
  filtered_services: string[];
}

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

export type UserDto = User;

import { Component, OnInit } from '@angular/core';
import { SupplierDto } from '../../models/types';
import {FormsModule} from '@angular/forms';
import {DatePipe} from '@angular/common';

// Defined locally because this is missing from types.ts but expected by the template
export interface AuditLogEntry {
  created_at: string;
  action_type: string;
  supplier_name: string;
  invoice_number: string;
  product_name: string;
  quantity_before: number;
  quantity_after: number;
  quantity_changed: number;
  used_wipe_code?: string;
  note?: string;
}

@Component({
  selector: 'app-audit-log',
  templateUrl: './audit-log.component.html',
  imports: [
    FormsModule,
    DatePipe
  ],
  styleUrls: ['./audit-log.component.scss']
})
export class AuditLogComponent implements OnInit {
  logs: AuditLogEntry[] = [];
  suppliers: SupplierDto[] = [];
  selectedAction: string = '';
  selectedSupplierId: number | null = null;
  isLoading = false;

  ngOnInit(): void {
    this.loadLogs();
  }

  loadLogs(): void {
    // Implement log fetching logic here
  }
}

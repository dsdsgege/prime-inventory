import { Component, OnInit, inject, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { InventoryItemView, InventoryStatus } from '../../models/types';

/**
 * AuditLogComponent – repurposed as an inventory status history / overview page.
 * The Spring Boot backend does not (yet) expose an audit log endpoint, so this
 * component shows the full inventory list allowing status filtering.
 */
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

  items: InventoryItemView[] = [];
  statusFilter: InventoryStatus | '' = '';
  searchQuery = '';
  isLoading = true;

  readonly statusOptions: { value: InventoryStatus | ''; label: string }[] = [
    { value: '', label: 'Összes állapot' },
    { value: 'IN_STOCK', label: 'Raktáron' },
    { value: 'SOLD', label: 'Eladott' },
    { value: 'RETURNED', label: 'Visszáru' },
    { value: 'DAMAGED', label: 'Sérült' },
    { value: 'RESERVED', label: 'Foglalt' },
  ];

  ngOnInit(): void {
    this.loadItems();
  }

  loadItems(): void {
    this.isLoading = true;
    this.cdr.markForCheck();
    this.api.getInventory({
      status: this.statusFilter || null,
      search: this.searchQuery.trim() || null
    }).subscribe({
      next: (data) => {
        this.items = data;
        this.isLoading = false;
        this.cdr.markForCheck();
      },
      error: () => {
        this.isLoading = false;
        this.cdr.markForCheck();
      }
    });
  }

  statusLabel(status: InventoryStatus): string {
    return this.statusOptions.find(o => o.value === status)?.label ?? status;
  }
}

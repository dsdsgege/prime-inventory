import { Routes } from '@angular/router';
import { StockComponent } from './components/stock/stock.component';
import { ReceivingComponent } from './components/receiving/receiving.component';
import { SalesComponent } from './components/sales/sales.component';
import { UploadComponent } from './components/upload/upload.component';
import { SuppliersComponent } from './components/suppliers/suppliers.component';
import { AuditLogComponent } from './components/audit-log/audit-log.component';
import { LoginComponent } from './components/login/login.component';
import { authGuard, loginGuard } from './guards/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    component: LoginComponent,
    canActivate: [loginGuard]
  },
  {
    path: '',
    redirectTo: 'stock',
    pathMatch: 'full'
  },
  {
    path: 'stock',
    component: StockComponent,
    canActivate: [authGuard]
  },
  {
    path: 'receiving',
    component: ReceivingComponent,
    canActivate: [authGuard]
  },
  {
    path: 'sales',
    component: SalesComponent,
    canActivate: [authGuard]
  },
  {
    path: 'upload',
    component: UploadComponent,
    canActivate: [authGuard]
  },
  {
    path: 'suppliers',
    component: SuppliersComponent,
    canActivate: [authGuard]
  },
  {
    path: 'logs',
    component: AuditLogComponent,
    canActivate: [authGuard]
  },
  {
    path: '**',
    redirectTo: 'stock'
  }
];

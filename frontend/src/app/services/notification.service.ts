import { Injectable, signal } from '@angular/core';

export type NotificationType = 'success' | 'error' | 'warning' | 'info';

export interface ToastNotification {
  id: string;
  type: NotificationType;
  title?: string;
  message: string;
  duration?: number;
}

@Injectable({
  providedIn: 'root'
})
export class NotificationService {
  private readonly toastsSignal = signal<ToastNotification[]>([]);
  readonly toasts = this.toastsSignal.asReadonly();

  show(type: NotificationType, message: string, title?: string, duration: number = 6000): void {
    const id = Math.random().toString(36).substring(2, 9);
    const newToast: ToastNotification = { id, type, message, title, duration };

    this.toastsSignal.update(list => [...list, newToast]);

    if (duration > 0) {
      setTimeout(() => {
        this.dismiss(id);
      }, duration);
    }
  }

  success(message: string, title: string = 'Sikeres művelet'): void {
    this.show('success', message, title);
  }

  error(message: string, title: string = 'Hiba történt'): void {
    this.show('error', message, title, 8000);
  }

  warning(message: string, title: string = 'Figyelmeztetés'): void {
    this.show('warning', message, title);
  }

  info(message: string, title: string = 'Információ'): void {
    this.show('info', message, title);
  }

  handleHttpError(err: any, fallbackMessage: string = 'Váratlan szerverhiba történt'): void {
    let msg = fallbackMessage;
    if (err && err.error && err.error.detail) {
      msg = err.error.detail;
    } else if (err && err.message) {
      msg = err.message;
    }
    this.error(msg, err?.status === 409 ? 'Számla ütközés' : 'Hiba történt');
  }

  dismiss(id: string): void {
    this.toastsSignal.update(list => list.filter(t => t.id !== id));
  }
}

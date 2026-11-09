import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { NotificationService } from '../services/notification.service';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const notificationService = inject(NotificationService);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      // Determine user-friendly title and message
      let message = 'Váratlan hiba történt a kiszolgálóval való kommunikáció során.';
      let title = 'Hiba történt';

      if (error.status === 401) {
        if (req.url.includes('/api/auth/login')) {
          title = 'Sikertelen Bejelentkezés';
          message = error.error?.message || 'Hibás felhasználónév vagy jelszó.';
        } else {
          // Token expired or invalid, user will be redirected by jwtInterceptor
          return throwError(() => error);
        }
      } else if (error.status === 409) {
        title = 'Konfliktus / Duplikáció';
        message = error.error?.message || error.error?.detail || 'Ez a tétel vagy számla már rögzítve van az adatbázisban.';
      } else if (error.status === 400) {
        title = 'Érvénytelen kérés';
        message = error.error?.message || error.error?.detail || 'A megadott adatok nem megfelelőek.';
      } else if (error.status === 404) {
        title = 'Nem található';
        message = error.error?.message || error.error?.detail || 'A kért elem nem található.';
      } else if (error.status >= 500) {
        title = 'Szerverhiba';
        message = error.error?.message || error.error?.detail || 'A szerver nem tudta feldolgozni a kérést.';
      } else if (error.status === 0) {
        title = 'Kapcsolati Hiba';
        message = 'A háttérkiszolgáló nem érhető el. Kérlek ellenőrizd, hogy fut-e a szerver.';
      } else if (error.error?.message || error.error?.detail) {
        message = error.error.message || error.error.detail;
      }

      notificationService.error(message, title);
      return throwError(() => error);
    })
  );
};

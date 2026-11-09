import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

/**
 * Attaches JWT Bearer token to outgoing HTTP requests and handles 401 Unauthorized responses.
 */
export const jwtInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const token = authService.getToken();

  let authReq = req;

  // Only attach token for internal API requests and when not logging in
  if (token && req.url.startsWith('/api') && !req.url.includes('/api/auth/login')) {
    authReq = req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    });
  }

  return next(authReq).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401 && !req.url.includes('/api/auth/login')) {
        authService.logout();
      }
      return throwError(() => error);
    })
  );
};

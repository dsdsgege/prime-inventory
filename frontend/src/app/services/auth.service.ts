import { Injectable, inject, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { LoginRequest, LoginResponse, User } from '../models/types';

const TOKEN_KEY = 'prime_auth_token';
const USER_KEY = 'prime_auth_user';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  // Reactive state signals
  readonly currentUser = signal<User | null>(null);
  readonly token = signal<string | null>(null);

  // Computed state
  readonly isAuthenticated = computed(() => !!this.token() && !!this.currentUser());
  readonly isAdmin = computed(() => this.currentUser()?.role === 'ROLE_ADMIN');

  constructor() {
    this.restoreSession();
  }

  /**
   * Performs login against the backend /api/auth/login endpoint
   */
  login(credentials: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>('/api/auth/login', credentials).pipe(
      tap((response) => {
        this.setSession(response);
      })
    );
  }

  /**
   * Logs out user, clears storage and redirects to /login
   */
  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this.token.set(null);
    this.currentUser.set(null);
    this.router.navigate(['/login']);
  }

  /**
   * Returns the current raw JWT token
   */
  getToken(): string | null {
    return this.token();
  }

  /**
   * Restores session from localStorage if token is still valid
   */
  private restoreSession(): void {
    try {
      const storedToken = localStorage.getItem(TOKEN_KEY);
      const storedUser = localStorage.getItem(USER_KEY);

      if (storedToken && storedUser) {
        if (this.isTokenExpired(storedToken)) {
          this.logout();
          return;
        }

        this.token.set(storedToken);
        this.currentUser.set(JSON.parse(storedUser));
      }
    } catch {
      this.logout();
    }
  }

  /**
   * Saves credentials and user metadata to reactive signals & localStorage
   */
  private setSession(authResult: LoginResponse): void {
    const user: User = {
      username: authResult.username,
      fullName: authResult.fullName,
      role: authResult.role
    };

    localStorage.setItem(TOKEN_KEY, authResult.token);
    localStorage.setItem(USER_KEY, JSON.stringify(user));

    this.token.set(authResult.token);
    this.currentUser.set(user);
  }

  /**
   * Validates JWT expiration without external dependencies
   */
  private isTokenExpired(token: string): boolean {
    try {
      const parts = token.split('.');
      if (parts.length !== 3) return true;
      const payload = JSON.parse(atob(parts[1]));
      if (!payload.exp) return false;
      return payload.exp * 1000 < Date.now();
    } catch {
      return true;
    }
  }
}

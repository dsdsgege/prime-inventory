import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { AuthService } from '../../services/auth.service';
import { NotificationService } from '../../services/notification.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss'
})
export class LoginComponent {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly notificationService = inject(NotificationService);

  username = '';
  password = '';
  isLoading = signal(false);
  errorMessage = signal<string | null>(null);

  onSubmit(): void {
    if (!this.username.trim() || !this.password) {
      this.errorMessage.set('Kérlek add meg a felhasználónevet és a jelszót.');
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.authService.login({
      username: this.username.trim(),
      password: this.password
    }).subscribe({
      next: (res) => {
        this.isLoading.set(false);
        this.notificationService.success(`Sikeres bejelentkezés! Üdv, ${res.fullName || res.username}!`, 'Belépve');
        const returnUrl = this.route.snapshot.queryParams['returnUrl'] || '/stock';
        this.router.navigateByUrl(returnUrl);
      },
      error: (err) => {
        this.isLoading.set(false);
        const msg = err.error?.message || 'Hibás felhasználónév vagy jelszó.';
        this.errorMessage.set(msg);
      }
    });
  }
}

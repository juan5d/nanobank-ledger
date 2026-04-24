import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss'
})
export class LoginComponent {
  mode = signal<'login' | 'register'>('login');
  error = signal<string>('');
  loading = signal<boolean>(false);

  loginForm = { email: '', password: '' };
  registerForm = { username: '', email: '', password: '' };

  constructor(private auth: AuthService, private router: Router) {}

  submit(): void {
    this.error.set('');
    this.loading.set(true);

    const obs = this.mode() === 'login'
      ? this.auth.login(this.loginForm)
      : this.auth.register(this.registerForm);

    obs.subscribe({
      next: () => this.router.navigate(['/dashboard']),
      error: () => {
        this.error.set('Credenciales inválidas. Por favor intenta de nuevo.');
        this.loading.set(false);
      }
    });
  }

  toggleMode(): void {
    this.mode.update(m => m === 'login' ? 'register' : 'login');
    this.error.set('');
  }
}

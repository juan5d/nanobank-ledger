import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { tap } from 'rxjs/operators';
import { Observable } from 'rxjs';
import { AuthResponse, LoginRequest, RegisterRequest } from '../models/auth.model';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly API = 'http://localhost:8080/api/auth';
  private readonly TOKEN_KEY = 'nanobank_token';

  isAuthenticated = signal<boolean>(this.hasToken());

  constructor(private http: HttpClient, private router: Router) {}

  register(request: RegisterRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.API}/register`, request).pipe(
      tap(res => this.storeToken(res.token))
    );
  }

  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.API}/login`, request).pipe(
      tap(res => this.storeToken(res.token))
    );
  }

  logout(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    this.isAuthenticated.set(false);
    this.router.navigate(['/login']);
  }

  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  private storeToken(token: string): void {
    localStorage.setItem(this.TOKEN_KEY, token);
    this.isAuthenticated.set(true);
  }

  private hasToken(): boolean {
    return !!localStorage.getItem(this.TOKEN_KEY);
  }
}

import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Wallet, WalletRequest } from '../models/wallet.model';

@Injectable({ providedIn: 'root' })
export class WalletService {
  private readonly API = 'http://localhost:8080/api/wallets';

  constructor(private http: HttpClient) {}

  getAll(): Observable<Wallet[]> {
    return this.http.get<Wallet[]>(this.API);
  }

  getById(id: number): Observable<Wallet> {
    return this.http.get<Wallet>(`${this.API}/${id}`);
  }

  create(request: WalletRequest): Observable<Wallet> {
    return this.http.post<Wallet>(this.API, request);
  }
}

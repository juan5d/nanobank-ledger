import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  MoveTransactionRequest,
  Transaction,
  TransactionRequest,
  TransferRequest
} from '../models/transaction.model';

@Injectable({ providedIn: 'root' })
export class TransactionService {
  private readonly API = 'http://localhost:8080/api/transactions';

  constructor(private http: HttpClient) {}

  getByWallet(walletId: number, category?: string, startDate?: string, endDate?: string): Observable<Transaction[]> {
    let params = new HttpParams().set('walletId', walletId);
    if (category) params = params.set('category', category);
    if (startDate) params = params.set('startDate', startDate);
    if (endDate) params = params.set('endDate', endDate);
    return this.http.get<Transaction[]>(this.API, { params });
  }

  create(request: TransactionRequest): Observable<Transaction> {
    return this.http.post<Transaction>(this.API, request);
  }

  transfer(request: TransferRequest): Observable<Transaction> {
    return this.http.post<Transaction>(`${this.API}/transfer`, request);
  }

  move(request: MoveTransactionRequest): Observable<Transaction> {
    return this.http.post<Transaction>(`${this.API}/move`, request);
  }
}

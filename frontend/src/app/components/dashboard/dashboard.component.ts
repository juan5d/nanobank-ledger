import { Component, OnInit, signal, computed, inject, ChangeDetectionStrategy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { CdkDragDrop, DragDropModule } from '@angular/cdk/drag-drop';
import { Wallet } from '../../models/wallet.model';
import { Transaction, TransactionRequest } from '../../models/transaction.model';
import { WalletService } from '../../services/wallet.service';
import { TransactionService } from '../../services/transaction.service';
import { AuthService } from '../../services/auth.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, DragDropModule],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DashboardComponent implements OnInit {
  private walletService = inject(WalletService);
  private transactionService = inject(TransactionService);
  private authService = inject(AuthService);

  wallets = signal<Wallet[]>([]);
  selectedWallet = signal<Wallet | null>(null);
  transactions = signal<Transaction[]>([]);

  categoryFilter = signal<string>('');
  startDate = signal<string>('');
  endDate = signal<string>('');

  showNewWallet = signal<boolean>(false);
  showNewTransaction = signal<boolean>(false);
  newWalletName = signal<string>('');
  errorMessage = signal<string>('');

  newTransaction: TransactionRequest = { description: '', amount: 0, type: 'INCOME', category: '', walletId: 0 };

  filteredTransactions = computed(() => {
    const cat = this.categoryFilter().toLowerCase();
    if (!cat) return this.transactions();
    return this.transactions().filter(t => t.category?.toLowerCase().includes(cat));
  });

  walletIds = computed(() => this.wallets().map(w => `wallet-${w.id}`));

  ngOnInit(): void {
    this.loadWallets();
  }

  loadWallets(): void {
    this.walletService.getAll().subscribe(wallets => {
      this.wallets.set(wallets);
      if (wallets.length > 0 && !this.selectedWallet()) {
        this.selectWallet(wallets[0]);
      }
    });
  }

  selectWallet(wallet: Wallet): void {
    this.selectedWallet.set(wallet);
    this.loadTransactions(wallet.id);
  }

  loadTransactions(walletId: number): void {
    const start = this.startDate() || undefined;
    const end = this.endDate() || undefined;
    this.transactionService.getByWallet(walletId, undefined, start, end)
      .subscribe(txns => this.transactions.set(txns));
  }

  createWallet(): void {
    const name = this.newWalletName().trim();
    if (!name) return;
    this.walletService.create({ name }).subscribe({
      next: () => {
        this.newWalletName.set('');
        this.showNewWallet.set(false);
        this.loadWallets();
      },
      error: (err: HttpErrorResponse) => this.showError(err)
    });
  }

  createTransaction(): void {
    const wallet = this.selectedWallet();
    if (!wallet) return;
    const req: TransactionRequest = { ...this.newTransaction, walletId: wallet.id };
    this.transactionService.create(req).subscribe({
      next: () => {
        this.showNewTransaction.set(false);
        this.newTransaction = { description: '', amount: 0, type: 'INCOME', category: '', walletId: 0 };
        this.loadWallets();
        this.loadTransactions(wallet.id);
      },
      error: (err: HttpErrorResponse) => this.showError(err)
    });
  }

  onTransactionDrop(event: CdkDragDrop<Transaction[]>, targetWallet: Wallet): void {
    const transaction: Transaction = event.item.data;
    if (transaction.walletId === targetWallet.id) return;

    this.transactionService.move({
      transactionId: transaction.id,
      targetWalletId: targetWallet.id
    }).subscribe({
      next: () => {
        this.loadWallets();
        const sel = this.selectedWallet();
        if (sel) this.loadTransactions(sel.id);
      },
      error: (err: HttpErrorResponse) => this.showError(err)
    });
  }

  applyDateFilter(): void {
    const sel = this.selectedWallet();
    if (sel) this.loadTransactions(sel.id);
  }

  logout(): void {
    this.authService.logout();
  }

  dismissError(): void {
    this.errorMessage.set('');
  }

  private showError(err: HttpErrorResponse): void {
    const mensajes: Record<number, string> = {
      422: 'Saldo insuficiente en la billetera para cubrir este gasto.',
      404: 'No se encontró el recurso solicitado.',
      400: 'Datos inválidos. Verifica los campos del formulario.',
      401: 'Sesión expirada. Por favor inicia sesión nuevamente.',
    };
    const mensaje = err.error?.message
      ? this.traducirMensaje(err.error.message)
      : (mensajes[err.status] ?? 'Ocurrió un error inesperado. Intenta de nuevo.');

    this.errorMessage.set(mensaje);
    setTimeout(() => this.errorMessage.set(''), 5000);
  }

  private traducirMensaje(backendMessage: string): string {
    if (backendMessage.toLowerCase().includes('insufficient funds')) {
      return 'Saldo insuficiente en la billetera para cubrir este gasto.';
    }
    if (backendMessage.toLowerCase().includes('wallet not found')) {
      return 'La billetera indicada no existe o no te pertenece.';
    }
    if (backendMessage.toLowerCase().includes('transaction not found')) {
      return 'El movimiento indicado no existe o no te pertenece.';
    }
    if (backendMessage.toLowerCase().includes('invalid credentials')) {
      return 'Credenciales inválidas.';
    }
    return backendMessage;
  }

  getWalletConnections(): string[] {
    return this.wallets().map(w => `wallet-${w.id}`);
  }
}

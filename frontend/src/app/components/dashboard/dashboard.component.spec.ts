import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { DashboardComponent } from './dashboard.component';
import { WalletService } from '../../services/wallet.service';
import { TransactionService } from '../../services/transaction.service';
import { AuthService } from '../../services/auth.service';

describe('DashboardComponent', () => {
  let component: DashboardComponent;
  let walletServiceSpy: jasmine.SpyObj<WalletService>;
  let transactionServiceSpy: jasmine.SpyObj<TransactionService>;
  let authServiceSpy: jasmine.SpyObj<AuthService>;

  const mockWallet = { id: 1, name: 'Ahorros', balance: 500 };
  const mockTransaction = { id: 1, description: 'Salario', amount: 1000,
    type: 'INCOME' as const, category: 'SALARY', date: '2026-04-24T10:00:00', walletId: 1 };

  beforeEach(async () => {
    walletServiceSpy = jasmine.createSpyObj('WalletService', ['getAll', 'create']);
    transactionServiceSpy = jasmine.createSpyObj('TransactionService', ['getByWallet', 'create', 'move', 'transfer', 'delete']);
    authServiceSpy = jasmine.createSpyObj('AuthService', ['logout']);

    walletServiceSpy.getAll.and.returnValue(of([mockWallet]));
    transactionServiceSpy.getByWallet.and.returnValue(of([mockTransaction]));

    await TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        { provide: WalletService, useValue: walletServiceSpy },
        { provide: TransactionService, useValue: transactionServiceSpy },
        { provide: AuthService, useValue: authServiceSpy }
      ]
    }).compileComponents();

    const fixture = TestBed.createComponent(DashboardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('debería crearse el componente', () => {
    expect(component).toBeTruthy();
  });

  it('debería cargar las billeteras al iniciar', () => {
    expect(walletServiceSpy.getAll).toHaveBeenCalled();
    expect(component.wallets().length).toBe(1);
    expect(component.wallets()[0].name).toBe('Ahorros');
  });

  it('debería seleccionar la primera billetera automáticamente', () => {
    expect(component.selectedWallet()?.id).toBe(1);
  });

  it('createTransaction: debería mostrar error 422 cuando el saldo es insuficiente', () => {
    const error = new HttpErrorResponse({
      status: 422,
      error: { message: 'Insufficient funds in wallet: Ahorros' }
    });
    transactionServiceSpy.create.and.returnValue(throwError(() => error));
    component.selectedWallet.set(mockWallet);
    component.newTransaction = { description: 'Gasto', amount: 9999,
      type: 'EXPENSE', category: 'X', walletId: 1 };

    component.createTransaction();

    expect(component.errorMessage()).toBe(
      'Saldo insuficiente en la billetera para cubrir este gasto.'
    );
  });

  it('createTransaction: debería mostrar error 400 cuando los datos son inválidos', () => {
    const error = new HttpErrorResponse({ status: 400, error: {} });
    transactionServiceSpy.create.and.returnValue(throwError(() => error));
    component.selectedWallet.set(mockWallet);

    component.createTransaction();

    expect(component.errorMessage()).toBe(
      'Datos inválidos. Verifica los campos del formulario.'
    );
  });

  it('createTransaction: debería recargar datos y limpiar formulario en éxito', () => {
    transactionServiceSpy.create.and.returnValue(of(mockTransaction));
    walletServiceSpy.getAll.and.returnValue(of([mockWallet]));
    transactionServiceSpy.getByWallet.and.returnValue(of([mockTransaction]));
    component.selectedWallet.set(mockWallet);
    component.newTransaction = { description: 'Test', amount: 100,
      type: 'INCOME', category: 'X', walletId: 1 };

    component.createTransaction();

    expect(component.showNewTransaction()).toBeFalse();
    expect(component.newTransaction.amount).toBe(0);
    expect(walletServiceSpy.getAll).toHaveBeenCalled();
  });

  it('dismissError: debería limpiar el mensaje de error', () => {
    component.errorMessage.set('Algún error');
    component.dismissError();
    expect(component.errorMessage()).toBe('');
  });

  it('filteredTransactions: debería filtrar por categoría en tiempo real', () => {
    component.transactions.set([
      { ...mockTransaction, category: 'FOOD' },
      { ...mockTransaction, id: 2, category: 'SALARY' }
    ]);

    component.categoryFilter.set('food');
    expect(component.filteredTransactions().length).toBe(1);
    expect(component.filteredTransactions()[0].category).toBe('FOOD');
  });

  it('filteredTransactions: debería devolver todos cuando el filtro está vacío', () => {
    component.transactions.set([mockTransaction, { ...mockTransaction, id: 2 }]);
    component.categoryFilter.set('');
    expect(component.filteredTransactions().length).toBe(2);
  });

  it('walletIds: debería generar IDs reactivos para CDK DnD', () => {
    component.wallets.set([{ id: 1, name: 'A', balance: 0 }, { id: 2, name: 'B', balance: 0 }]);
    expect(component.walletIds()).toEqual(['wallet-1', 'wallet-2']);
  });

  it('logout: debería llamar al AuthService', () => {
    component.logout();
    expect(authServiceSpy.logout).toHaveBeenCalled();
  });
});

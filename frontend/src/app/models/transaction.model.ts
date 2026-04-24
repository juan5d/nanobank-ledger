export type TransactionType = 'INCOME' | 'EXPENSE';

export interface Transaction {
  id: number;
  description: string;
  amount: number;
  type: TransactionType;
  category: string;
  date: string;
  walletId: number;
}

export interface TransactionRequest {
  description: string;
  amount: number;
  type: TransactionType;
  category: string;
  walletId: number;
}

export interface TransferRequest {
  fromWalletId: number;
  toWalletId: number;
  amount: number;
  description?: string;
}

export interface MoveTransactionRequest {
  transactionId: number;
  targetWalletId: number;
}

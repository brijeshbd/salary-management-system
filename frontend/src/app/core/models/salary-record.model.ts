import { Currency, SalaryChangeReason } from './reference-data';

export interface SalaryRecord {
  id: number;
  baseSalary: number;
  currency: Currency;
  effectiveDate: string;
  reason: SalaryChangeReason;
  createdAt: string;
}

export interface SalaryAdjustmentRequest {
  baseSalary: number;
  currency: Currency;
  effectiveDate: string;
  reason: SalaryChangeReason;
}

import { Currency, SalaryChangeReason } from './reference-data';

/** One (group, currency) pair - the backend never sums across currencies (see backend
 * docs/tradeoffs.md), so a department with employees in three currencies produces three rows. */
export interface GroupSalarySummary {
  group: string;
  currency: Currency;
  headcount: number;
  avgSalary: number;
  medianSalary: number;
  totalCost: number;
}

export interface CurrencyTotal {
  currency: Currency;
  headcount: number;
  totalCost: number;
}

export interface PayDistributionBucket {
  rangeStart: number;
  rangeEnd: number;
  count: number;
}

export interface RecentRaise {
  employeeCode: string;
  firstName: string;
  lastName: string;
  department: string;
  newSalary: number;
  currency: Currency;
  effectiveDate: string;
  reason: SalaryChangeReason;
}

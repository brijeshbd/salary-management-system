export type Country = 'US' | 'GB' | 'IN' | 'DE' | 'CA' | 'AU' | 'SG';
export type Currency = 'USD' | 'GBP' | 'INR' | 'EUR' | 'CAD' | 'AUD' | 'SGD';
export type JobGrade = 'IC1' | 'IC2' | 'IC3' | 'IC4' | 'IC5' | 'IC6' | 'M1' | 'M2' | 'M3' | 'M4';
export type SalaryChangeReason = 'INITIAL' | 'RAISE' | 'PROMOTION' | 'ADJUSTMENT' | 'CORRECTION';

export const COUNTRIES: Country[] = ['US', 'GB', 'IN', 'DE', 'CA', 'AU', 'SG'];
export const CURRENCIES: Currency[] = ['USD', 'GBP', 'INR', 'EUR', 'CAD', 'AUD', 'SGD'];
export const JOB_GRADES: JobGrade[] = ['IC1', 'IC2', 'IC3', 'IC4', 'IC5', 'IC6', 'M1', 'M2', 'M3', 'M4'];
export const SALARY_CHANGE_REASONS: SalaryChangeReason[] = ['RAISE', 'PROMOTION', 'ADJUSTMENT', 'CORRECTION'];

/** One currency per country in this app (see backend docs/tradeoffs.md) - used to default the
 * currency field when a user picks a country on the add-employee form. */
export const DEFAULT_CURRENCY_FOR_COUNTRY: Record<Country, Currency> = {
  US: 'USD',
  GB: 'GBP',
  IN: 'INR',
  DE: 'EUR',
  CA: 'CAD',
  AU: 'AUD',
  SG: 'SGD',
};

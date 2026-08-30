import { Pipe, PipeTransform } from '@angular/core';

/** Angular's built-in `currency` pipe needs one app-wide default currency; this app has none -
 * every salary carries its own currency (see backend docs/tradeoffs.md, no FX conversion), so
 * each amount must be formatted with the code that came with it. */
@Pipe({ name: 'currencyByCode', standalone: true })
export class CurrencyByCodePipe implements PipeTransform {
  transform(amount: number | null | undefined, currencyCode: string | null | undefined): string {
    if (amount == null || !currencyCode) {
      return '—';
    }
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: currencyCode,
      maximumFractionDigits: 2,
    }).format(amount);
  }
}

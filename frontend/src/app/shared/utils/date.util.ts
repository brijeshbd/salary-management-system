/** Formats a Date as YYYY-MM-DD using its local calendar fields - `date.toISOString()` converts
 * to UTC first, which silently shifts the date by one day for any user west of UTC picking
 * "today" (or any date) near midnight local time. */
export function toIsoDate(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

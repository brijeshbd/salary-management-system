/** Triggers a browser "Save As" for an in-memory Blob (e.g. a CSV response body) - there's no
 * server-side URL to link to, so a temporary object URL + synthetic anchor click is the standard
 * way to hand the browser a file it didn't itself request. */
export function downloadBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  URL.revokeObjectURL(url);
}

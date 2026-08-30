import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { EmployeeSearchParams } from '../models/employee.model';
import { ImportSummary } from '../models/import-summary.model';
import { toHttpParams } from './employee.service';

@Injectable({ providedIn: 'root' })
export class ImportExportService {
  private readonly baseUrl = `${environment.apiBaseUrl}/employees`;

  constructor(private readonly http: HttpClient) {}

  importCsv(file: File): Observable<ImportSummary> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<ImportSummary>(`${this.baseUrl}/import`, formData);
  }

  /** Filters, not pagination - the backend export endpoint ignores page/size and returns every
   * matching row (see backend docs/tradeoffs.md). */
  exportCsv(filters: Omit<EmployeeSearchParams, 'page' | 'size'>): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/export`, { params: toHttpParams(filters), responseType: 'blob' });
  }
}

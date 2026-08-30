import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { SalaryAdjustmentRequest, SalaryRecord } from '../models/salary-record.model';

@Injectable({ providedIn: 'root' })
export class SalaryService {
  constructor(private readonly http: HttpClient) {}

  getHistory(employeeId: number): Observable<SalaryRecord[]> {
    return this.http.get<SalaryRecord[]>(`${environment.apiBaseUrl}/employees/${employeeId}/salary-history`);
  }

  addRecord(employeeId: number, request: SalaryAdjustmentRequest): Observable<SalaryRecord> {
    return this.http.post<SalaryRecord>(`${environment.apiBaseUrl}/employees/${employeeId}/salary-history`, request);
  }
}

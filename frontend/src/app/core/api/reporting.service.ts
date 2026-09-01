import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { CurrencyTotal, GroupSalarySummary, PayDistributionBucket, RecentRaise } from '../models/report.model';
import { Currency } from '../models/reference-data';

@Injectable({ providedIn: 'root' })
export class ReportingService {
  private readonly baseUrl = `${environment.apiBaseUrl}/reports`;

  constructor(private readonly http: HttpClient) {}

  summaryByDepartment(): Observable<GroupSalarySummary[]> {
    return this.http.get<GroupSalarySummary[]>(`${this.baseUrl}/summary/by-department`);
  }

  exportSummaryByDepartment(): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/summary/by-department/export`, { responseType: 'blob' });
  }

  summaryByCountry(): Observable<GroupSalarySummary[]> {
    return this.http.get<GroupSalarySummary[]>(`${this.baseUrl}/summary/by-country`);
  }

  exportSummaryByCountry(): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/summary/by-country/export`, { responseType: 'blob' });
  }

  summaryByGrade(): Observable<GroupSalarySummary[]> {
    return this.http.get<GroupSalarySummary[]>(`${this.baseUrl}/summary/by-grade`);
  }

  exportSummaryByGrade(): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/summary/by-grade/export`, { responseType: 'blob' });
  }

  headcountCost(): Observable<CurrencyTotal[]> {
    return this.http.get<CurrencyTotal[]>(`${this.baseUrl}/headcount-cost`);
  }

  exportHeadcountCost(): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/headcount-cost/export`, { responseType: 'blob' });
  }

  payDistribution(currency: Currency): Observable<PayDistributionBucket[]> {
    return this.http.get<PayDistributionBucket[]>(`${this.baseUrl}/pay-distribution`, {
      params: new HttpParams().set('currency', currency),
    });
  }

  exportPayDistribution(currency: Currency): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/pay-distribution/export`, {
      params: new HttpParams().set('currency', currency),
      responseType: 'blob',
    });
  }

  raisesSince(since: string): Observable<RecentRaise[]> {
    return this.http.get<RecentRaise[]>(`${this.baseUrl}/raises`, { params: new HttpParams().set('since', since) });
  }

  exportRaises(since: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/raises/export`, {
      params: new HttpParams().set('since', since),
      responseType: 'blob',
    });
  }
}

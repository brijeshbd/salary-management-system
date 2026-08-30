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

  summaryByCountry(): Observable<GroupSalarySummary[]> {
    return this.http.get<GroupSalarySummary[]>(`${this.baseUrl}/summary/by-country`);
  }

  summaryByGrade(): Observable<GroupSalarySummary[]> {
    return this.http.get<GroupSalarySummary[]>(`${this.baseUrl}/summary/by-grade`);
  }

  headcountCost(): Observable<CurrencyTotal[]> {
    return this.http.get<CurrencyTotal[]>(`${this.baseUrl}/headcount-cost`);
  }

  payDistribution(currency: Currency): Observable<PayDistributionBucket[]> {
    return this.http.get<PayDistributionBucket[]>(`${this.baseUrl}/pay-distribution`, {
      params: new HttpParams().set('currency', currency),
    });
  }

  raisesSince(since: string): Observable<RecentRaise[]> {
    return this.http.get<RecentRaise[]>(`${this.baseUrl}/raises`, { params: new HttpParams().set('since', since) });
  }
}

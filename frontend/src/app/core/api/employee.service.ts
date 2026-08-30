import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  Employee,
  EmployeeCreateRequest,
  EmployeeSearchParams,
  EmployeeUpdateRequest,
  PageResponse,
} from '../models/employee.model';

@Injectable({ providedIn: 'root' })
export class EmployeeService {
  private readonly baseUrl = `${environment.apiBaseUrl}/employees`;

  constructor(private readonly http: HttpClient) {}

  search(params: EmployeeSearchParams): Observable<PageResponse<Employee>> {
    return this.http.get<PageResponse<Employee>>(this.baseUrl, { params: toHttpParams(params) });
  }

  getById(id: number): Observable<Employee> {
    return this.http.get<Employee>(`${this.baseUrl}/${id}`);
  }

  create(request: EmployeeCreateRequest): Observable<Employee> {
    return this.http.post<Employee>(this.baseUrl, request);
  }

  update(id: number, request: EmployeeUpdateRequest): Observable<Employee> {
    return this.http.put<Employee>(`${this.baseUrl}/${id}`, request);
  }

  deactivate(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
  }
}

/** Skips undefined/null/empty-string values rather than sending them as literal "undefined"
 * query params. */
export function toHttpParams(params: object): HttpParams {
  let httpParams = new HttpParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') {
      httpParams = httpParams.set(key, String(value));
    }
  }
  return httpParams;
}

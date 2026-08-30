import { Country, Currency, JobGrade } from './reference-data';

export interface CurrentSalary {
  amount: number;
  currency: Currency;
  effectiveDate: string;
}

export interface Employee {
  id: number;
  employeeCode: string;
  firstName: string;
  lastName: string;
  department: string;
  country: Country;
  jobGrade: JobGrade;
  active: boolean;
  currentSalary: CurrentSalary | null;
  createdAt: string;
  updatedAt: string;
}

export interface EmployeeCreateRequest {
  firstName: string;
  lastName: string;
  department: string;
  country: Country;
  jobGrade: JobGrade;
  baseSalary: number;
  currency: Currency;
  effectiveDate: string;
}

export interface EmployeeUpdateRequest {
  firstName: string;
  lastName: string;
  department: string;
  country: Country;
  jobGrade: JobGrade;
}

export interface EmployeeSearchParams {
  search?: string;
  department?: string;
  country?: Country;
  jobGrade?: JobGrade;
  minSalary?: number;
  maxSalary?: number;
  active?: boolean;
  page?: number;
  size?: number;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

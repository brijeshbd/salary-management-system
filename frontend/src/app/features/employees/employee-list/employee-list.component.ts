import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatToolbarModule } from '@angular/material/toolbar';
import { Router, RouterLink } from '@angular/router';
import { debounceTime } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { ImportExportService } from '../../../core/api/import-export.service';
import { EmployeeService } from '../../../core/api/employee.service';
import { Employee, EmployeeSearchParams } from '../../../core/models/employee.model';
import { COUNTRIES, Country, DEPARTMENTS, JOB_GRADES, JobGrade } from '../../../core/models/reference-data';
import { CurrencyByCodePipe } from '../../../shared/pipes/currency-by-code.pipe';
import { downloadBlob } from '../../../shared/utils/download.util';

@Component({
  selector: 'app-employee-list',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    MatToolbarModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatTableModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    CurrencyByCodePipe,
  ],
  templateUrl: './employee-list.component.html',
  styleUrl: './employee-list.component.scss',
})
export class EmployeeListComponent implements OnInit {
  private readonly employeeService = inject(EmployeeService);
  private readonly importExportService = inject(ImportExportService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly formBuilder = inject(FormBuilder);

  readonly countries = COUNTRIES;
  readonly jobGrades = JOB_GRADES;
  readonly departments = DEPARTMENTS;
  readonly displayedColumns = [
    'employeeCode',
    'name',
    'department',
    'country',
    'jobGrade',
    'currentSalary',
    'active',
  ];

  readonly employees = signal<Employee[]>([]);
  readonly totalElements = signal(0);
  readonly loading = signal(false);
  readonly pageIndex = signal(0);
  readonly pageSize = signal(20);

  readonly filterForm = this.formBuilder.group({
    search: [''],
    department: [''],
    country: [''],
    jobGrade: [''],
    minSalary: [''],
    maxSalary: [''],
  });

  ngOnInit(): void {
    this.fetchEmployees();

    this.filterForm.valueChanges.pipe(debounceTime(300)).subscribe(() => {
      this.pageIndex.set(0);
      this.fetchEmployees();
    });
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
    this.fetchEmployees();
  }

  openEmployee(employee: Employee): void {
    this.router.navigate(['/employees', employee.id]);
  }

  exportCsv(): void {
    this.importExportService.exportCsv(this.currentFilters()).subscribe((blob) => downloadBlob(blob, 'employees.csv'));
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  private fetchEmployees(): void {
    this.loading.set(true);

    this.employeeService
      .search({ ...this.currentFilters(), page: this.pageIndex(), size: this.pageSize() })
      .subscribe({
        next: (page) => {
          this.employees.set(page.content);
          this.totalElements.set(page.totalElements);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  private currentFilters(): Omit<EmployeeSearchParams, 'page' | 'size'> {
    const { search, department, country, jobGrade, minSalary, maxSalary } = this.filterForm.getRawValue();
    return {
      search: search || undefined,
      department: department || undefined,
      country: (country as Country) || undefined,
      jobGrade: (jobGrade as JobGrade) || undefined,
      minSalary: minSalary ? Number(minSalary) : undefined,
      maxSalary: maxSalary ? Number(maxSalary) : undefined,
    };
  }
}

import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatToolbarModule } from '@angular/material/toolbar';
import { Router } from '@angular/router';
import { EmployeeService } from '../../../core/api/employee.service';
import { EmployeeCreateRequest } from '../../../core/models/employee.model';
import {
  COUNTRIES,
  CURRENCIES,
  Country,
  DEFAULT_CURRENCY_FOR_COUNTRY,
  DEPARTMENTS,
  JOB_GRADES,
} from '../../../core/models/reference-data';
import { NumericOnlyDirective } from '../../../shared/directives/numeric-only.directive';
import { toIsoDate } from '../../../shared/utils/date.util';

@Component({
  selector: 'app-employee-form',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatToolbarModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatDatepickerModule,
    NumericOnlyDirective,
  ],
  templateUrl: './employee-form.component.html',
  styleUrl: './employee-form.component.scss',
})
export class EmployeeFormComponent {
  private readonly formBuilder = inject(FormBuilder);
  private readonly employeeService = inject(EmployeeService);
  private readonly router = inject(Router);

  readonly countries = COUNTRIES;
  readonly currencies = CURRENCIES;
  readonly jobGrades = JOB_GRADES;
  readonly departments = DEPARTMENTS;
  readonly saving = signal(false);

  readonly form = this.formBuilder.group({
    firstName: ['', Validators.required],
    lastName: ['', Validators.required],
    department: ['', Validators.required],
    country: ['', Validators.required],
    jobGrade: ['', Validators.required],
    baseSalary: ['', [Validators.required, Validators.min(0.01)]],
    currency: ['', Validators.required],
    effectiveDate: [new Date(), Validators.required],
  });

  onCountryChange(country: string): void {
    const currencyControl = this.form.controls.currency;
    if (!currencyControl.dirty) {
      currencyControl.setValue(DEFAULT_CURRENCY_FOR_COUNTRY[country as Country] ?? '');
    }
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.saving.set(true);
    const raw = this.form.getRawValue();
    const request: EmployeeCreateRequest = {
      firstName: raw.firstName!,
      lastName: raw.lastName!,
      department: raw.department!,
      country: raw.country as EmployeeCreateRequest['country'],
      jobGrade: raw.jobGrade as EmployeeCreateRequest['jobGrade'],
      baseSalary: Number(raw.baseSalary),
      currency: raw.currency as EmployeeCreateRequest['currency'],
      effectiveDate: toIsoDate(raw.effectiveDate as Date),
    };

    this.employeeService.create(request).subscribe({
      next: (employee) => {
        this.saving.set(false);
        this.router.navigate(['/employees', employee.id]);
      },
      error: () => this.saving.set(false),
    });
  }

  cancel(): void {
    this.router.navigate(['/employees']);
  }
}

import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatToolbarModule } from '@angular/material/toolbar';
import { ActivatedRoute, Router } from '@angular/router';
import { EmployeeService } from '../../../core/api/employee.service';
import { SalaryService } from '../../../core/api/salary.service';
import { Employee, EmployeeUpdateRequest } from '../../../core/models/employee.model';
import { COUNTRIES, JOB_GRADES } from '../../../core/models/reference-data';
import { SalaryRecord, SalaryAdjustmentRequest } from '../../../core/models/salary-record.model';
import { ConfirmDialogComponent } from '../../../shared/components/confirm-dialog/confirm-dialog.component';
import { CurrencyByCodePipe } from '../../../shared/pipes/currency-by-code.pipe';
import { SalaryAdjustmentDialogComponent } from '../salary-adjustment-dialog/salary-adjustment-dialog.component';

@Component({
  selector: 'app-employee-detail',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatToolbarModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatTableModule,
    MatChipsModule,
    MatDialogModule,
    MatProgressSpinnerModule,
    CurrencyByCodePipe,
  ],
  templateUrl: './employee-detail.component.html',
  styleUrl: './employee-detail.component.scss',
})
export class EmployeeDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly employeeService = inject(EmployeeService);
  private readonly salaryService = inject(SalaryService);
  private readonly dialog = inject(MatDialog);
  private readonly formBuilder = inject(FormBuilder);

  readonly countries = COUNTRIES;
  readonly jobGrades = JOB_GRADES;
  readonly historyColumns = ['effectiveDate', 'baseSalary', 'reason'];

  readonly employee = signal<Employee | null>(null);
  readonly history = signal<SalaryRecord[]>([]);
  readonly loading = signal(true);
  readonly editing = signal(false);
  readonly saving = signal(false);

  readonly form = this.formBuilder.group({
    firstName: ['', Validators.required],
    lastName: ['', Validators.required],
    department: ['', Validators.required],
    country: ['', Validators.required],
    jobGrade: ['', Validators.required],
  });

  private employeeId!: number;

  ngOnInit(): void {
    this.employeeId = Number(this.route.snapshot.paramMap.get('id'));
    this.loadEmployee();
    this.loadHistory();
  }

  startEditing(): void {
    const employee = this.employee();
    if (!employee) return;
    this.form.setValue({
      firstName: employee.firstName,
      lastName: employee.lastName,
      department: employee.department,
      country: employee.country,
      jobGrade: employee.jobGrade,
    });
    this.editing.set(true);
  }

  cancelEditing(): void {
    this.editing.set(false);
  }

  saveEdits(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.saving.set(true);
    this.employeeService.update(this.employeeId, this.form.getRawValue() as EmployeeUpdateRequest).subscribe({
      next: (employee) => {
        this.employee.set(employee);
        this.editing.set(false);
        this.saving.set(false);
      },
      error: () => this.saving.set(false),
    });
  }

  openAdjustmentDialog(): void {
    const dialogRef = this.dialog.open(SalaryAdjustmentDialogComponent, { width: '420px' });
    dialogRef.afterClosed().subscribe((request: SalaryAdjustmentRequest | undefined) => {
      if (!request) return;
      this.salaryService.addRecord(this.employeeId, request).subscribe(() => {
        this.loadEmployee();
        this.loadHistory();
      });
    });
  }

  deactivate(): void {
    const dialogRef = this.dialog.open(ConfirmDialogComponent, {
      data: {
        title: 'Deactivate employee?',
        message: 'This marks the employee inactive. Their record and salary history are kept.',
        confirmLabel: 'Deactivate',
      },
    });
    dialogRef.afterClosed().subscribe((confirmed) => {
      if (confirmed) {
        this.employeeService.deactivate(this.employeeId).subscribe(() => this.loadEmployee());
      }
    });
  }

  reactivate(): void {
    this.employeeService.reactivate(this.employeeId).subscribe((employee) => this.employee.set(employee));
  }

  goBack(): void {
    this.router.navigate(['/employees']);
  }

  private loadEmployee(): void {
    this.loading.set(true);
    this.employeeService.getById(this.employeeId).subscribe({
      next: (employee) => {
        this.employee.set(employee);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  private loadHistory(): void {
    this.salaryService.getHistory(this.employeeId).subscribe((history) => this.history.set(history));
  }
}

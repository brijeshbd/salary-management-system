import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { NumericOnlyDirective } from '../../../shared/directives/numeric-only.directive';
import { toIsoDate } from '../../../shared/utils/date.util';
import { SalaryAdjustmentRequest } from '../../../core/models/salary-record.model';
import { CURRENCIES, SALARY_CHANGE_REASONS } from '../../../core/models/reference-data';

@Component({
  selector: 'app-salary-adjustment-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatDatepickerModule,
    MatButtonModule,
    NumericOnlyDirective,
  ],
  templateUrl: './salary-adjustment-dialog.component.html',
})
export class SalaryAdjustmentDialogComponent {
  private readonly dialogRef = inject(MatDialogRef<SalaryAdjustmentDialogComponent, SalaryAdjustmentRequest>);
  private readonly formBuilder = inject(FormBuilder);

  readonly currencies = CURRENCIES;
  readonly reasons = SALARY_CHANGE_REASONS;

  readonly form = this.formBuilder.group({
    baseSalary: ['', [Validators.required, Validators.min(0.01)]],
    currency: ['', Validators.required],
    effectiveDate: [new Date(), Validators.required],
    reason: ['RAISE', Validators.required],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const { baseSalary, currency, effectiveDate, reason } = this.form.getRawValue();
    this.dialogRef.close({
      baseSalary: Number(baseSalary),
      currency: currency as SalaryAdjustmentRequest['currency'],
      effectiveDate: toIsoDate(effectiveDate as Date),
      reason: reason as SalaryAdjustmentRequest['reason'],
    });
  }

  cancel(): void {
    this.dialogRef.close();
  }
}

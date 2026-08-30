import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { MatToolbarModule } from '@angular/material/toolbar';
import { Router } from '@angular/router';
import { ChartConfiguration } from 'chart.js';
import { BaseChartDirective, provideCharts, withDefaultRegisterables } from 'ng2-charts';
import { forkJoin } from 'rxjs';
import { ReportingService } from '../../../core/api/reporting.service';
import { CurrencyTotal, GroupSalarySummary, PayDistributionBucket, RecentRaise } from '../../../core/models/report.model';
import { CURRENCIES, Currency } from '../../../core/models/reference-data';
import { CurrencyByCodePipe } from '../../../shared/pipes/currency-by-code.pipe';
import { toIsoDate } from '../../../shared/utils/date.util';

/** Single hue for magnitude-only bar charts (headcount, pay-distribution counts) - position/
 * labels already carry identity, so a categorical rainbow per bar would be noise, not signal.
 * From the dataviz skill's validated default palette, categorical slot 1 (light mode). */
const CHART_COLOR = '#2a78d6';

@Component({
  selector: 'app-reports-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    MatToolbarModule,
    MatButtonModule,
    MatSelectModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatTableModule,
    MatProgressSpinnerModule,
    CurrencyByCodePipe,
    BaseChartDirective,
  ],
  // Registered here, not app.config.ts - this component is the only lazy-loaded consumer of
  // chart.js, and app.routes.ts is always eagerly loaded, so a provider (or import) placed there
  // would still pull chart.js into the main bundle for every route.
  providers: [provideCharts(withDefaultRegisterables())],
  templateUrl: './reports-dashboard.component.html',
  styleUrl: './reports-dashboard.component.scss',
})
export class ReportsDashboardComponent implements OnInit {
  private readonly reportingService = inject(ReportingService);
  private readonly router = inject(Router);

  readonly currencies = CURRENCIES;
  readonly loading = signal(true);

  readonly totalHeadcount = signal(0);
  readonly costByCurrency = signal<CurrencyTotal[]>([]);
  readonly byDepartment = signal<GroupSalarySummary[]>([]);
  readonly byGrade = signal<GroupSalarySummary[]>([]);
  readonly byCountry = signal<GroupSalarySummary[]>([]);

  readonly summaryColumns = ['group', 'currency', 'headcount', 'avgSalary', 'medianSalary', 'totalCost'];

  readonly selectedCurrency = signal<Currency>('USD');
  readonly distributionLoading = signal(false);
  readonly payDistributionChart = signal<ChartConfiguration<'bar'>['data']>({ labels: [], datasets: [] });

  readonly countryHeadcountChart = signal<ChartConfiguration<'bar'>['data']>({ labels: [], datasets: [] });

  readonly chartOptions: ChartConfiguration<'bar'>['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { display: false } },
    scales: { y: { beginAtZero: true } },
  };

  readonly raisesSince = signal<Date>(defaultRaisesSinceDate());
  readonly raises = signal<RecentRaise[]>([]);
  readonly raisesLoading = signal(false);
  readonly raiseColumns = ['employeeCode', 'name', 'department', 'newSalary', 'effectiveDate', 'reason'];

  ngOnInit(): void {
    this.loadOverview();
    this.loadPayDistribution();
    this.loadRaises();
  }

  onCurrencyChange(currency: Currency): void {
    this.selectedCurrency.set(currency);
    this.loadPayDistribution();
  }

  onRaisesSinceChange(date: Date | null): void {
    if (!date) return;
    this.raisesSince.set(date);
    this.loadRaises();
  }

  goToEmployees(): void {
    this.router.navigate(['/employees']);
  }

  private loadOverview(): void {
    this.loading.set(true);
    forkJoin({
      headcountCost: this.reportingService.headcountCost(),
      byDepartment: this.reportingService.summaryByDepartment(),
      byGrade: this.reportingService.summaryByGrade(),
      byCountry: this.reportingService.summaryByCountry(),
    }).subscribe(({ headcountCost, byDepartment, byGrade, byCountry }) => {
      this.costByCurrency.set(headcountCost);
      this.totalHeadcount.set(headcountCost.reduce((sum, row) => sum + row.headcount, 0));
      this.byDepartment.set(byDepartment);
      this.byGrade.set(byGrade);
      this.byCountry.set(byCountry);

      // Headcount by country is currency-agnostic (a plain count), so - unlike avg/median/total
      // salary - it's safe to chart directly across countries without mixing currency scales.
      this.countryHeadcountChart.set({
        labels: byCountry.map((row) => row.group),
        datasets: [{ data: byCountry.map((row) => row.headcount), label: 'Headcount', backgroundColor: CHART_COLOR }],
      });

      this.loading.set(false);
    });
  }

  private loadPayDistribution(): void {
    this.distributionLoading.set(true);
    this.reportingService.payDistribution(this.selectedCurrency()).subscribe((buckets) => {
      this.payDistributionChart.set(toDistributionChartData(buckets, this.selectedCurrency()));
      this.distributionLoading.set(false);
    });
  }

  private loadRaises(): void {
    this.raisesLoading.set(true);
    this.reportingService.raisesSince(toIsoDate(this.raisesSince())).subscribe((raises) => {
      this.raises.set(raises);
      this.raisesLoading.set(false);
    });
  }
}

function toDistributionChartData(
  buckets: PayDistributionBucket[],
  currency: Currency,
): ChartConfiguration<'bar'>['data'] {
  const formatter = new Intl.NumberFormat('en-US', { style: 'currency', currency, maximumFractionDigits: 0 });
  return {
    labels: buckets.map((bucket) => `${formatter.format(bucket.rangeStart)} - ${formatter.format(bucket.rangeEnd)}`),
    datasets: [{ data: buckets.map((bucket) => bucket.count), label: 'Employees', backgroundColor: CHART_COLOR }],
  };
}

function defaultRaisesSinceDate(): Date {
  const date = new Date();
  date.setFullYear(date.getFullYear() - 1);
  return date;
}

import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';
import { MatToolbarModule } from '@angular/material/toolbar';
import { Router } from '@angular/router';
import { ImportExportService } from '../../../core/api/import-export.service';
import { ImportSummary } from '../../../core/models/import-summary.model';

@Component({
  selector: 'app-csv-import',
  standalone: true,
  imports: [MatToolbarModule, MatButtonModule, MatIconModule, MatProgressSpinnerModule, MatTableModule],
  templateUrl: './csv-import.component.html',
  styleUrl: './csv-import.component.scss',
})
export class CsvImportComponent {
  private readonly importExportService = inject(ImportExportService);
  private readonly router = inject(Router);

  readonly selectedFile = signal<File | null>(null);
  readonly uploading = signal(false);
  readonly summary = signal<ImportSummary | null>(null);
  readonly errorColumns = ['row', 'message'];

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.selectedFile.set(input.files?.[0] ?? null);
    this.summary.set(null);
  }

  upload(): void {
    const file = this.selectedFile();
    if (!file) return;

    this.uploading.set(true);
    this.importExportService.importCsv(file).subscribe({
      next: (summary) => {
        this.summary.set(summary);
        this.uploading.set(false);
      },
      error: () => this.uploading.set(false),
    });
  }

  goToEmployees(): void {
    this.router.navigate(['/employees']);
  }
}

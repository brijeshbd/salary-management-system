export interface ImportRowError {
  row: number;
  message: string;
}

export interface ImportSummary {
  totalRows: number;
  succeeded: number;
  failed: number;
  errors: ImportRowError[];
}

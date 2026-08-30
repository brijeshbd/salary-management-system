import { Routes } from '@angular/router';
import { authGuard } from './core/auth/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/login/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'employees',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/employees/employee-list/employee-list.component').then((m) => m.EmployeeListComponent),
  },
  {
    path: 'employees/new',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/employees/employee-form/employee-form.component').then((m) => m.EmployeeFormComponent),
  },
  {
    path: 'employees/:id',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/employees/employee-detail/employee-detail.component').then((m) => m.EmployeeDetailComponent),
  },
  { path: '', pathMatch: 'full', redirectTo: 'employees' },
  { path: '**', redirectTo: 'employees' },
];

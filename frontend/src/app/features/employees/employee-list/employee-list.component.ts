import { Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatToolbarModule } from '@angular/material/toolbar';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/auth/auth.service';

/** Placeholder for the M7 scaffold milestone - proves the login -> guard -> protected-route round
 * trip works end-to-end. Fleshed out into the real paginated/filterable employee table in the
 * next milestone. */
@Component({
  selector: 'app-employee-list',
  standalone: true,
  imports: [MatToolbarModule, MatButtonModule],
  template: `
    <mat-toolbar color="primary">
      <span>Salary Management System</span>
      <span class="spacer"></span>
      <button mat-button (click)="logout()">Logout</button>
    </mat-toolbar>
    <div class="content">
      <p>You're signed in. The employee list, search, and reports land in the next milestone.</p>
    </div>
  `,
  styles: [
    `
      .spacer {
        flex: 1 1 auto;
      }
      .content {
        padding: 24px;
      }
    `,
  ],
})
export class EmployeeListComponent {
  constructor(
    private readonly authService: AuthService,
    private readonly router: Router,
  ) {}

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}

import { Directive, HostListener } from '@angular/core';

/** Blocks non-numeric keystrokes on salary inputs at the point of entry, rather than only
 * validating after the fact - a decimal point is allowed, letters and symbols are not. */
@Directive({ selector: '[appNumericOnly]', standalone: true })
export class NumericOnlyDirective {
  @HostListener('beforeinput', ['$event'])
  onBeforeInput(event: InputEvent): void {
    if (event.data && !/^[0-9.]*$/.test(event.data)) {
      event.preventDefault();
    }
  }
}

import { forwardRef, type ComponentProps } from 'react';
import { ChoiceControl } from './ChoiceControl';

export type CheckboxProps = Omit<ComponentProps<typeof ChoiceControl>, 'type'>;

export const Checkbox = forwardRef<HTMLInputElement, CheckboxProps>(function Checkbox(props, ref) {
  return <ChoiceControl ref={ref} type="checkbox" {...props} />;
});

import { forwardRef, type ComponentProps } from 'react';
import { ChoiceControl } from './ChoiceControl';

export type RadioProps = Omit<ComponentProps<typeof ChoiceControl>, 'type'>;

/** Group several Radios with the same `name` inside a <fieldset> + <legend> (see FormSection). */
export const Radio = forwardRef<HTMLInputElement, RadioProps>(function Radio(props, ref) {
  return <ChoiceControl ref={ref} type="radio" {...props} />;
});

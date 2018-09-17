#ifndef MOTOR_PWM_H_
#define MOTOR_PWM_H_

#define MOTOR_PWM_FREQ 360 // 72000kHz/2/4 /360 = 20kHz

void motors_PWM_init();
void motors_PWM_set(unsigned char channel, signed char val);
void motors_PWM_off_by_timer();

#endif /* MOTOR_PWM_H_ */

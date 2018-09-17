#ifndef GLOBAL_H_
#define GLOBAL_H_

#include "stm32f10x.h"
#include "stm32f10x_conf.h"
#include "stm32f10x_rtc.h"

#include "hw_config.h"
#include "USART_io.h"

#include "motor_pwm.h"
#include "cmdSPI.h"
#include "battery_monitor.h"

#define LED_ENABLE // PC13-LED
#define DBG_LED_PIN GPIO_Pin_13
#define DBG_LED_PORT GPIOC


extern volatile uint32_t _sysTicks;
//void delayMs(uint32_t msec);
//void delayUs(uint32_t usec);

// auto power off
#define POWER_OFF_PWM_TIMEOUT 400 // ms. power off for motors
#define POWER_OFF_TORCH_TIMEOUT 5000
extern volatile uint32_t _pwm_timeout;
extern volatile uint32_t _torch_timeout;

// ============================================================
//  Tx 	- PA10 (USART1_Rx)
//  Rx 	- PA9 (USART1_Tx)
//----------
// SPI
// PB13 - SPI2_SCK, PB14 - SPI2_MISO, PB15 - SPI2_MOSI
//----------
// PWM car motors
// left: PA1 - TIM2_CH2, PA6 (L298N in1,in3), PA5 (L298N in2,in4)
// right: PA2 - TIM2_CH3, PA3 (L298N in1,in3), PA4 (L298N in2,in4)
// ---------
// PA0 (ADC123_IN0) - Battery voltage control
// ---------
// PA7 - Torch
// =============================================================
#endif /* GLOBAL_H_ */

#ifndef __HW_CONFIG_H
#define __HW_CONFIG_H

void SystemStartup(void);

//#define LED_ENABLE

#ifdef LED_ENABLE //current consumption GPIO C - 0.47mA
#define DBG_LED_PIN GPIO_Pin_13
#define DBG_LED_PORT GPIOC
#endif

#endif  /*__HW_CONFIG_H*/

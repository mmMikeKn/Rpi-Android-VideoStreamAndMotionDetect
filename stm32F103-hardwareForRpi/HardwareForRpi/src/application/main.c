#include <stdlib.h>
#include <string.h>
#include "global.h"

void delayMs(uint32_t msec) {
	uint32_t tmp = 7000 * msec;
	while (tmp--)
		__NOP();
}

void delayUs(uint32_t usec) {
	uint32_t tmp = 7 * usec;
	while (tmp--)
		__NOP();
}

int main() {
	SystemStartup();
#ifdef LED_ENABLE
	DBG_LED_PORT->BRR = DBG_LED_PIN;
	_torch_timeout = 1000;
#endif
	motors_PWM_init();
	spi_cmd_init();
	battery_monitor_init();

#ifdef USART1_ENABLE
	USART_init();
	USART_DBG_printf("start v1.0 %s\n", __TIMESTAMP__);
#endif

	while (1) {
#ifdef USART1_ENABLE
		//delayMs(2000);
		//USART_DBG_printf("V: %d\n\r", battery_monitor_get());
#endif
	}
}


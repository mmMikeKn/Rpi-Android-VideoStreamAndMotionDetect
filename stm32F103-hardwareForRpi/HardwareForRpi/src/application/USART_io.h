#ifndef USART_IO_H_
#define USART_IO_H_

//#define USART2_ENABLE
#define USART1_ENABLE

//------------------- for stm32f10x_it.h
#ifdef USART2_ENABLE
void USART2_IT_RxReady_exec(uint8_t c);
void USART2_IT_TxReady_exec();
#endif

#ifdef USART1_ENABLE
void USART1_IT_RxReady_exec(uint8_t c);
void USART1_IT_TxReady_exec();
#endif
//--------------------

#ifdef USART1_ENABLE && USART2_ENABLE
void USART_init();
#endif

//======================================
#ifdef USART2_ENABLE
#define  CMD_ST_NO_CMD (-1)
#define  CMD_ST_WRONG_DATA (-2)
#define  CMD_ST_TIMEOUT_DATA (-3)

void CI_putCmd(uint8_t code, const uint8_t *data, int sz);
short CI_getLastCmdCode(void);
uint8_t CI_getCmdBody(uint8_t *data, int maxSz);
#endif
//======================================
#ifdef USART1_ENABLE
#define USART_DBG_TX_BUFFER_SZ 512
void USART_DBG_putc(char c);
void USART_DBG_puts(char *str);
void USART_DBG_hexDump(uint8_t *bin, uint8_t len);
void USART_DBG_bin(uint8_t *bin, uint16_t len);
char *USART_DBG_printf(const char* str, ...);
#else
#define USART_DBG_printf(...) {}
#endif
//======================================

#endif

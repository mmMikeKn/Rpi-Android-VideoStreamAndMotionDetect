#ifndef BATTERY_MONITOR_H_
#define BATTERY_MONITOR_H_

void battery_monitor_init(void);
void battery_monitor_fix(void);
uint16_t battery_monitor_get(void);

#endif /* BATTERY_MONITOR_H_ */

#include "global.h"

void motors_PWM_init() {
	RCC_APB2PeriphClockCmd(RCC_APB2Periph_AFIO, ENABLE);
	RCC_APB1PeriphClockCmd(RCC_APB1Periph_TIM2, ENABLE);

	// PWM car motors
	// left: PA1 - TIM2_CH2, PA6 (L298N in1,in3), PA5 (L298N in2,in4)
	// right: PA2 - TIM2_CH3, PA3 (L298N in1,in3), PA4 (L298N in2,in4)
	// ---------

	GPIO_InitTypeDef GPIO_InitStructure;
	GPIO_InitStructure.GPIO_Speed = GPIO_Speed_2MHz;
	GPIO_InitStructure.GPIO_Mode = GPIO_Mode_AF_PP;
	GPIO_InitStructure.GPIO_Pin = GPIO_Pin_1 | GPIO_Pin_2;
	GPIO_Init(GPIOA, &GPIO_InitStructure);

	GPIO_InitStructure.GPIO_Mode = GPIO_Mode_Out_PP;
	GPIO_InitStructure.GPIO_Pin = GPIO_Pin_3 | GPIO_Pin_4 | GPIO_Pin_5
			| GPIO_Pin_6;
	GPIO_Init(GPIOA, &GPIO_InitStructure);

	TIM_TimeBaseInitTypeDef TIM_TimeBaseStructure;

	TIM_TimeBaseStructInit(&TIM_TimeBaseStructure);
	TIM_TimeBaseStructure.TIM_Period = MOTOR_PWM_FREQ;
	TIM_TimeBaseStructure.TIM_Prescaler = 6;
	TIM_TimeBaseStructure.TIM_ClockDivision = 0;
	TIM_TimeBaseStructure.TIM_CounterMode = TIM_CounterMode_Up;
	TIM_TimeBaseInit(TIM2, &TIM_TimeBaseStructure);

	TIM_OCInitTypeDef TIM_OCInitStructure;
	TIM_OCStructInit(&TIM_OCInitStructure);
	TIM_OCInitStructure.TIM_OCMode = TIM_OCMode_PWM1;
	TIM_OCInitStructure.TIM_OutputState = TIM_OutputState_Enable;
	TIM_OCInitStructure.TIM_Pulse = 0;
	TIM_OCInitStructure.TIM_OCPolarity = TIM_OCPolarity_High;

	TIM_OC2Init(TIM2, &TIM_OCInitStructure);
	TIM_OC2PreloadConfig(TIM2, TIM_OCPreload_Enable);
	TIM_CCxCmd(TIM2, TIM_Channel_2, TIM_CCx_Disable);

	TIM_OC3Init(TIM2, &TIM_OCInitStructure);
	TIM_OC3PreloadConfig(TIM2, TIM_OCPreload_Enable);
	TIM_CCxCmd(TIM2, TIM_Channel_3, TIM_CCx_Disable);

	TIM_ARRPreloadConfig(TIM2, ENABLE);
	TIM_Cmd(TIM2, ENABLE);
}

void motors_PWM_off_by_timer() {
	for (int i = 0; i < 2; i++)
		motors_PWM_set(i, 0); // off
}

void motors_PWM_set(unsigned char channel, signed char val) {
	// value = -100..0..100
	int v = val >= 0 ? val : -val;
	int pwm = MOTOR_PWM_FREQ * (int) v / 100;
	unsigned char in = 0;

	if (channel > 2)
		return;

	if (val < 0)
		in = !in;

	if (channel == 0) {
		if (val == 0)
			TIM_CCxCmd(TIM2, TIM_Channel_2, TIM_CCx_Disable);
		else {
			TIM_SetCompare2(TIM2, pwm);
			TIM_CCxCmd(TIM2, TIM_Channel_2, TIM_CCx_Enable);
		}
		GPIO_WriteBit(GPIOA, GPIO_Pin_6, in);
		GPIO_WriteBit(GPIOA, GPIO_Pin_5, !in);
	} else if (channel == 1) {
		if (val == 0)
			TIM_CCxCmd(TIM2, TIM_Channel_3, TIM_CCx_Disable);
		else {
			TIM_SetCompare3(TIM2, pwm);
			TIM_CCxCmd(TIM2, TIM_Channel_3, TIM_CCx_Enable);
		}
		GPIO_WriteBit(GPIOA, GPIO_Pin_3, in);
		GPIO_WriteBit(GPIOA, GPIO_Pin_4, !in);
	}
}


package org.onosproject.sse;

public enum SseTimerType {
	/**
	 * 增加定时安装intent的任务
	 */
	ADD_INSTALL_INTENT_TIMER,
	/**
	 * 手动取消定时任务
	 */
	CANCEL_INTENT_TIMER,
	/**
	 * 增加定时取消intent的任务
	 */
	ADD_WITHDRAW_INTENT_TIMER
	
	
}

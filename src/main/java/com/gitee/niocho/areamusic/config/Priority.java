package com.gitee.niocho.areamusic.config;

public enum Priority {
    // 最高优先级
    HIGHEST(3),
    // 次高优先级
    HIGHER(2),
    // 高优先级
    HIGH(1),
    // 正常优先级
    NORMAL(0),
    // 低优先级
    LOW(-1),
    // 次低优先级
    LOWER(-2),
    // 最低优先级
    LOWEST(-3);

    private final int value;

    Priority(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}

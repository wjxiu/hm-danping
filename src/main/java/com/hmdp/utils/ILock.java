package com.hmdp.utils;

/**
 * @author xiu
 * @create 2023-02-07 20:04
 */
public interface ILock {

    boolean tryLock(Long timeOutSec);
    void unlock();

}

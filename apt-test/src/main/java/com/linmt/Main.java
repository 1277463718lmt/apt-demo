package com.linmt;

import com.linmt.po.User;

import java.lang.reflect.Method;

/**
 * User: Linmt@datagoo.cn
 * Date: 2022/11/21
 * Time: 20:33
 * Description: No Description
 */
public class Main {

    public static void main(String[] args) {
        Method[] declaredMethods = User.class.getDeclaredMethods();
        for (Method method: declaredMethods) {
            System.out.println(method.getName());
        }
    }
}

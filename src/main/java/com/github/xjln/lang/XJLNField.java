package com.github.xjln.lang;

import java.lang.reflect.Field;

public record XJLNField(boolean inner, boolean constant, String type, String name, String initValue){

    public static XJLNField ofField(String name, Field field){
        boolean inner = field.toString().startsWith("private ") || field.toString().startsWith("protected ");
        boolean constant = field.toString().contains(" final ");
        String type = field.getType().toString().split(" ", 2)[1];

        return new XJLNField(inner, constant, type, name, null);
    }
}

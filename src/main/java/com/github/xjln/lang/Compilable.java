package com.github.xjln.lang;

import com.github.xjln.bytecode.AccessFlag;

public abstract sealed class Compilable permits XJLNClass, XJLNType {

    public final AccessFlag accessFlag;

    public Compilable(AccessFlag accessFlag){
        this.accessFlag = accessFlag;
    }

    public abstract int getAccessFlag();
}

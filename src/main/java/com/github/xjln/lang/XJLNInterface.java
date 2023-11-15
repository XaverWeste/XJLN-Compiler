package com.github.xjln.lang;

import com.github.xjln.bytecode.AccessFlag;

public final class XJLNInterface extends Compilable{
    public XJLNInterface(AccessFlag accessFlag) {
        super(accessFlag);
    }

    public int getAccessFlag() {
        return super.getAccessFlag() + AccessFlag.ABSTRACT + AccessFlag.INTERFACE;
    }
}

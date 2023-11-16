package com.github.xjln.lang;

import com.github.xjln.bytecode.AccessFlag;

public final class XJLNMethod extends Compilable{

    public final boolean statik, abstrakt, synchronise;

    public XJLNMethod(AccessFlag accessFlag, boolean statik, boolean abstrakt, boolean synchronise) {
        super(accessFlag);
        this.statik = statik;
        this.abstrakt = abstrakt;
        this.synchronise = synchronise;
    }

    @Override
    public int getAccessFlag() {
        int accessFlag = super.getAccessFlag();

        if(statik)
            accessFlag += AccessFlag.STATIC;

        if(abstrakt)
            accessFlag += AccessFlag.ABSTRACT;

        if(synchronise)
            accessFlag += AccessFlag.SYNCHRONIZED;

        return accessFlag;
    }
}

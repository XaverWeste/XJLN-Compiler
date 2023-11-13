package com.github.xjln.lang;

import com.github.xjln.bytecode.AccessFlag;

public record XJLNField(AccessFlag accessFlag, boolean statik, boolean transiend, boolean volatil, boolean constant, String type) {

    public int getAccessFlag(){
        int accessFlag = switch (accessFlag()){
            case ACC_PUBLIC -> AccessFlag.PUBLIC;
            case ACC_PRIVATE -> AccessFlag.PRIVATE;
            case ACC_PROTECTED -> AccessFlag.PROTECTED;
        };

        if(statik)
            accessFlag += AccessFlag.STATIC;

        if(transiend)
            accessFlag += AccessFlag.TRANSIENT;

        if(volatil)
            accessFlag += AccessFlag.VOLATILE;

        return accessFlag;
    }
}

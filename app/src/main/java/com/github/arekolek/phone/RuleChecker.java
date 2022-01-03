package com.github.arekolek.phone;

import java.lang.*;

public class RuleChecker {

    public boolean allowChecker(String numString) {
        String trueNum = numString.substring(4);
        Long number = Long.parseLong(trueNum);
        if (number ==  716710261L) {
            return false;
        } else {
            return true;
        }
    }
}


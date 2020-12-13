package org.vision.common.utils;

public class ActuatorRateUtil {
    public static long getActuatorRate(long headNumber) {
        long rate = 0;
        if(headNumber >=0 && headNumber < 2591280){
            rate = 3;
        }else if(headNumber >=2591280 && headNumber < 5182560){
            rate = 2;
        }else{
            rate = 1;
        }
        return rate;
    }
}

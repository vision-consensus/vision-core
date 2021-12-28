package org.vision.common.utils;

import java.sql.Timestamp;

public class Time {

  public static long getCurrentMillis() {
    return System.currentTimeMillis();
  }

  public static String getTimeString(long time) {
    return new Timestamp(time).toString();
  }

  public static String formatMillisInterval(long mss){
    String dateTimes = null;
    mss /= 1000;
    long days = mss / ( 60 * 60 * 24);
    long hours = (mss % ( 60 * 60 * 24)) / (60 * 60);
    long minutes = (mss % ( 60 * 60)) /60;
    long seconds = mss % 60;
    String hourStr = hours <= 9  ? "0" + hours : String.valueOf(hours);
    String minutesStr = minutes <= 9 ? "0" + minutes : String.valueOf(minutes);
    String secondsStr = seconds <= 9 ? "0" + seconds : String.valueOf(seconds);
    if(days > 0){
      dateTimes = days + "D " + hourStr + ":" + minutesStr + ":"  + secondsStr;
    }else{
      dateTimes = hourStr + ":" + minutesStr + ":"  + secondsStr;
    }

    return dateTimes;
  }
}

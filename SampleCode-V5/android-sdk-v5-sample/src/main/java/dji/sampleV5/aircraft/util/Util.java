package dji.sampleV5.aircraft.util;


import java.math.BigDecimal;
import java.math.RoundingMode;

public class Util {

    private Util(){
        //init something
    }

    public static boolean isNotBlank(String str) {
        return (str != null && str.trim().length() != 0);
    }

    /**
     * Truncate given double number to specified decimal places.
     * If original number has fewer decimal places than n, additional 0s will be added.
     * If n <= 0, all decimal places will be truncated
     * @param num original double
     * @param n target decimal places
     * @return double with n decimal places
     */
    public static double truncateToNDecimalPlaces(double num, int n){
        if (n <= 0){
            return (double)((long) num);
        }

        double magnitude = Math.pow(10, n);
        BigDecimal bdNum = new BigDecimal(num * magnitude);

        return ((double) bdNum.longValue())/magnitude;
    }

}



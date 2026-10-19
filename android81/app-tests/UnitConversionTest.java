package ro.leo.visual;
public final class UnitConversionTest {
    static int checks;
    static void close(double expected,double actual){checks++;if(Math.abs(expected-actual)>Math.max(1,Math.abs(expected))*1e-9)throw new AssertionError(expected+" != "+actual);}
    public static void main(String[] args){
        close(1000,UnitConversion.convert(0,3,2,1));close(2.54,UnitConversion.convert(0,4,1,1));close(1609.344,UnitConversion.convert(0,6,2,1));
        close(.45359237,UnitConversion.convert(1,4,1,1));close(32,UnitConversion.convert(2,0,1,0));close(100,UnitConversion.convert(2,1,0,212));close(0,UnitConversion.convert(2,0,2,-273.15));
        close(3.785411784,UnitConversion.convert(3,3,1,1));close(3.6,UnitConversion.convert(4,1,0,1));close(10000,UnitConversion.convert(5,2,0,1));
        for(int c=0;c<UnitConversion.UNITS.length;c++)for(int i=0;i<UnitConversion.UNITS[c].length;i++)for(int j=0;j<UnitConversion.UNITS[c].length;j++){
            double value=c==2?50:123.456;close(value,UnitConversion.convert(c,j,i,UnitConversion.convert(c,i,j,value)));
        }
        for(double invalid:new double[]{Double.NaN,Double.POSITIVE_INFINITY}){try{UnitConversion.convert(0,0,1,invalid);throw new AssertionError();}catch(IllegalArgumentException expected){checks++;}}
        try{UnitConversion.convert(2,2,0,-1);throw new AssertionError();}catch(IllegalArgumentException expected){checks++;}
        System.out.println("Unit conversion: "+checks+" checks passed");
    }
}
